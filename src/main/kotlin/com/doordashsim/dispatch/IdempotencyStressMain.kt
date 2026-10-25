package com.doordashsim.dispatch

import com.doordashsim.dispatch.proto.HandoffEvent
import java.util.UUID

/**
 * Measures the forced-broker-redelivery claim at full scale: produces FORCED_REDELIVERY_COUNT
 * distinct handoff events to a real Kafka topic, consumes them to first application, then rewinds
 * the consumer group to the start of the topic (exactly what a rebalance before an offset commit
 * does in production) and reprocesses every message a second time, counting how many of those
 * reprocessed messages are wrongly counted as newly applied. Requires DISPATCH_KAFKA_BOOTSTRAP and
 * DISPATCH_PG_URL/USER/PASSWORD pointing at reachable infrastructure.
 */
const val FORCED_REDELIVERY_COUNT = 50_000

fun main() {
    val bootstrap = System.getenv("DISPATCH_KAFKA_BOOTSTRAP") ?: "localhost:19092"
    val jdbcUrl = System.getenv("DISPATCH_PG_URL") ?: "jdbc:postgresql://localhost:54329/dispatch"
    val user = System.getenv("DISPATCH_PG_USER") ?: "dispatch"
    val password = System.getenv("DISPATCH_PG_PASSWORD") ?: "dispatch"

    val ledger = HandoffLedger(Db.dataSource(jdbcUrl, user, password))
    ledger.init()

    val topic = "handoffs-idempotency-stress-" + UUID.randomUUID()
    val orderIds = (0 until FORCED_REDELIVERY_COUNT).map { "stress-$it-" + UUID.randomUUID() }

    HandoffProducer(bootstrap).use { producer ->
        orderIds.forEach { orderId ->
            val event = HandoffEvent.newBuilder()
                .setHandoffId(UUID.randomUUID().toString())
                .setOrderId(orderId)
                .setState("CREATED")
                .setSequence(1)
                .build()
            producer.send(topic, event)
        }
    }
    println("produced=${orderIds.size}")

    val groupId = "idempotency-stress-group-" + UUID.randomUUID()
    var firstPassApplied = 0
    var firstPassDuplicate = 0
    HandoffConsumer(bootstrap, groupId, ledger).use { consumer ->
        consumer.subscribe(topic)
        var attempts = 0
        while (firstPassApplied + firstPassDuplicate < FORCED_REDELIVERY_COUNT && attempts < 200) {
            val (applied, duplicate) = consumer.pollAndApplyOnce(2000)
            firstPassApplied += applied
            firstPassDuplicate += duplicate
            attempts++
        }
    }
    println("first_pass_applied=$firstPassApplied")
    println("first_pass_duplicate=$firstPassDuplicate")

    var redeliveredApplied = 0
    var redeliveredDuplicate = 0
    HandoffConsumer(bootstrap, groupId, ledger).use { consumer ->
        consumer.subscribe(topic)
        consumer.seekToBeginningAllAssigned()
        var attempts = 0
        while (redeliveredApplied + redeliveredDuplicate < FORCED_REDELIVERY_COUNT && attempts < 200) {
            val (applied, duplicate) = consumer.pollAndApplyOnce(2000)
            redeliveredApplied += applied
            redeliveredDuplicate += duplicate
            attempts++
        }
    }

    println("forced_redeliveries=${redeliveredApplied + redeliveredDuplicate}")
    println("redelivered_wrongly_applied_as_new=$redeliveredApplied")
    println("redelivered_correctly_detected_as_duplicate=$redeliveredDuplicate")
    println("ledger_total_rows_this_topic_scope=${orderIds.size}")
}
