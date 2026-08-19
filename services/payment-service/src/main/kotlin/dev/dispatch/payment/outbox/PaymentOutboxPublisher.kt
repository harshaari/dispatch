package dev.dispatch.payment.outbox

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(prefix = "app.payment.outbox", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class PaymentOutboxPublisher(
    private val jdbcTemplate: JdbcTemplate,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${app.payment.result-topic}") private val topic: String,
    @Value("\${app.payment.outbox.batch-size}") private val batchSize: Int,
    @Value("\${app.payment.outbox.lease-duration}") private val leaseDuration: Duration,
) {
    private val relayId = UUID.randomUUID()
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.payment.outbox.fixed-delay}")
    fun publishAvailable() {
        claim().forEach { event ->
            try {
                kafkaTemplate.send(topic, event.aggregateId.toString(), event.payload).get(10, TimeUnit.SECONDS)
                jdbcTemplate.update(
                    "UPDATE outbox_events SET published_at = ?, locked_by = NULL, locked_until = NULL WHERE id = ? AND locked_by = ?",
                    Timestamp.from(Instant.now()), event.id, relayId,
                )
                logger.atInfo().addKeyValue("eventId", event.id).addKeyValue("eventType", event.eventType)
                    .log("Payment outbox event published")
            } catch (exception: Exception) {
                jdbcTemplate.update(
                    "UPDATE outbox_events SET attempts = attempts + 1, last_error = ?, locked_by = NULL, locked_until = NULL WHERE id = ? AND locked_by = ?",
                    (exception.message ?: exception.javaClass.simpleName).take(2_000), event.id, relayId,
                )
                logger.atWarn().setCause(exception).addKeyValue("eventId", event.id)
                    .log("Payment outbox event publication failed; it will be retried")
            }
        }
    }

    @Transactional
    fun claim(): List<ClaimedPaymentOutboxEvent> {
        val now = Instant.now()
        return jdbcTemplate.query(
            """
            WITH candidates AS (
                SELECT id FROM outbox_events
                WHERE published_at IS NULL AND (locked_until IS NULL OR locked_until < ?)
                ORDER BY occurred_at FOR UPDATE SKIP LOCKED LIMIT ?
            )
            UPDATE outbox_events event SET locked_by = ?, locked_until = ?
            FROM candidates WHERE event.id = candidates.id
            RETURNING event.id, event.aggregate_id, event.event_type, event.payload::text AS payload
            """.trimIndent(),
            { rs, _ -> ClaimedPaymentOutboxEvent(rs.getObject("id", UUID::class.java), rs.getObject("aggregate_id", UUID::class.java), rs.getString("event_type"), rs.getString("payload")) },
            Timestamp.from(now), batchSize, relayId, Timestamp.from(now.plus(leaseDuration)),
        )
    }
}

data class ClaimedPaymentOutboxEvent(val id: UUID, val aggregateId: UUID, val eventType: String, val payload: String)
