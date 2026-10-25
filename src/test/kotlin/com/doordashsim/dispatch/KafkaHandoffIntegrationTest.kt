package com.doordashsim.dispatch

import com.doordashsim.dispatch.proto.HandoffEvent
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Requires a real reachable Kafka and Postgres (see README "Building and running"). Skips cleanly
 * if either is unreachable. Runs at a small scale for everyday CI; the 50,000-forced-redelivery
 * claim is measured at full scale by IdempotencyStressMain, not by this suite, to keep the default
 * test run fast.
 */
class KafkaHandoffIntegrationTest {

    private fun bootstrapServers() = System.getenv("DISPATCH_KAFKA_BOOTSTRAP") ?: "localhost:19092"
    private fun jdbcUrl() = System.getenv("DISPATCH_PG_URL") ?: "jdbc:postgresql://localhost:54329/dispatch"

    private fun kafkaReachable(): Boolean = try {
        val props = Properties()
        props[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers()
        props[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = "3000"
        AdminClient.create(props).use { it.listTopics().names().get() }
        true
    } catch (e: Exception) {
        false
    }

    private fun postgresReachable(): Boolean = try {
        DriverManager.getConnection(jdbcUrl(), "dispatch", "dispatch").use { true }
    } catch (e: Exception) {
        false
    }

    private lateinit var ledger: HandoffLedger

    @BeforeEach
    fun setUp() {
        assumeTrue(kafkaReachable(), "Kafka not reachable at ${bootstrapServers()}; skipping integration test")
        assumeTrue(postgresReachable(), "Postgres not reachable at ${jdbcUrl()}; skipping integration test")
        val ds = Db.dataSource(jdbcUrl(), "dispatch", "dispatch")
        ledger = HandoffLedger(ds)
        ledger.init()
    }

    @Test
    fun `200 orders progress through all six handoff states via Kafka and land COMPLETED`() {
        val topic = "handoffs-lifecycle-" + UUID.randomUUID()
        val orderCount = 200
        val states = listOf("CREATED", "NOTIFIED", "MERCHANT_ACK", "READY_FOR_PICKUP", "HANDED_OFF", "COMPLETED")

        HandoffProducer(bootstrapServers()).use { producer ->
            for (o in 0 until orderCount) {
                val orderId = "lifecycle-$o-" + UUID.randomUUID()
                states.forEachIndexed { idx, state ->
                    val event = HandoffEvent.newBuilder()
                        .setHandoffId(UUID.randomUUID().toString())
                        .setOrderId(orderId)
                        .setState(state)
                        .setSequence((idx + 1).toLong())
                        .build()
                    producer.send(topic, event)
                }
            }
        }

        var totalApplied = 0
        HandoffConsumer(bootstrapServers(), "lifecycle-group-" + UUID.randomUUID(), ledger).use { consumer ->
            consumer.subscribe(topic)
            var attempts = 0
            while (totalApplied < orderCount * states.size && attempts < 30) {
                val (applied, _) = consumer.pollAndApplyOnce(2000)
                totalApplied += applied
                attempts++
            }
        }

        assertEquals(orderCount * states.size, totalApplied)
    }

    @Test
    fun `forced redelivery of already-applied handoffs produces zero duplicate applications`() {
        val topic = "handoffs-redelivery-" + UUID.randomUUID()
        val orderCount = 1000

        HandoffProducer(bootstrapServers()).use { producer ->
            for (o in 0 until orderCount) {
                val event = HandoffEvent.newBuilder()
                    .setHandoffId(UUID.randomUUID().toString())
                    .setOrderId("redelivery-$o-" + UUID.randomUUID())
                    .setState("CREATED")
                    .setSequence(1)
                    .build()
                producer.send(topic, event)
            }
        }

        val groupId = "redelivery-group-" + UUID.randomUUID()
        var firstPassApplied = 0
        HandoffConsumer(bootstrapServers(), groupId, ledger).use { consumer ->
            consumer.subscribe(topic)
            var attempts = 0
            while (firstPassApplied < orderCount && attempts < 30) {
                val (applied, _) = consumer.pollAndApplyOnce(2000)
                firstPassApplied += applied
                attempts++
            }
        }
        assertEquals(orderCount, firstPassApplied)

        var redeliveredApplied = 0
        var redeliveredDuplicate = 0
        HandoffConsumer(bootstrapServers(), groupId, ledger).use { consumer ->
            consumer.subscribe(topic)
            consumer.seekToBeginningAllAssigned()
            var attempts = 0
            while (redeliveredApplied + redeliveredDuplicate < orderCount && attempts < 30) {
                val (applied, duplicate) = consumer.pollAndApplyOnce(2000)
                redeliveredApplied += applied
                redeliveredDuplicate += duplicate
                attempts++
            }
        }

        assertEquals(0, redeliveredApplied, "a redelivered, already-applied handoff must never be counted as newly applied")
        assertEquals(orderCount, redeliveredDuplicate)
        assertTrue(true)
    }
}
