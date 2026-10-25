package com.doordashsim.dispatch

import com.doordashsim.dispatch.proto.HandoffEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.time.Duration
import java.util.Properties

/** Publishes HandoffEvent protobuf messages, keyed by order id so all of one order's events land on one partition and are read in order. */
class HandoffProducer(bootstrapServers: String) : AutoCloseable {
    private val producer: KafkaProducer<ByteArray, ByteArray>

    init {
        val props = Properties()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java.name
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java.name
        props[ProducerConfig.ACKS_CONFIG] = "all"
        producer = KafkaProducer(props)
    }

    fun send(topic: String, event: HandoffEvent) {
        val record = ProducerRecord(topic, event.orderId.toByteArray(), event.toByteArray())
        producer.send(record).get()
    }

    override fun close() {
        producer.close()
    }
}

/**
 * Consumes HandoffEvent messages and applies each one through the ledger. Exposes seekToBeginning
 * so a test can force real broker-level redelivery of already-applied messages, the same thing a
 * consumer-group rebalance before an offset commit would cause in production.
 */
class HandoffConsumer(bootstrapServers: String, groupId: String, private val ledger: HandoffLedger) : AutoCloseable {
    private val consumer: KafkaConsumer<ByteArray, ByteArray>

    init {
        val props = Properties()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = ByteArrayDeserializer::class.java.name
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = ByteArrayDeserializer::class.java.name
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
        consumer = KafkaConsumer(props)
    }

    fun subscribe(topic: String) {
        consumer.subscribe(listOf(topic))
    }

    /** Polls once and applies every record found, returning (applied, duplicate) counts. */
    fun pollAndApplyOnce(timeoutMs: Long = 2000): Pair<Int, Int> {
        val records = consumer.poll(Duration.ofMillis(timeoutMs))
        var applied = 0
        var duplicate = 0
        for (r in records) {
            val event = HandoffEvent.parseFrom(r.value())
            val result = ledger.apply(event.orderId, event.sequence, event.state, event.handoffId)
            if (result.applied) applied++ else duplicate++
        }
        consumer.commitSync()
        return applied to duplicate
    }

    /** Rewinds every assigned partition to the start, forcing already-applied messages to be redelivered on the next poll. */
    fun seekToBeginningAllAssigned() {
        var partitions: Collection<TopicPartition> = emptyList()
        var attempts = 0
        while (partitions.isEmpty() && attempts < 50) {
            consumer.poll(Duration.ofMillis(200))
            partitions = consumer.assignment()
            attempts++
        }
        require(partitions.isNotEmpty()) { "no partitions assigned after $attempts poll attempts; cannot force redelivery" }
        consumer.seekToBeginning(partitions)
    }

    override fun close() {
        consumer.close()
    }
}
