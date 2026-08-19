package dev.dispatch.payment.payment

import tools.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Component
class PaymentOutboxWriter(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun appendResult(payment: PaymentEntity) {
        val eventType = if (payment.status == PaymentStatus.AUTHORIZED) "PaymentAuthorized" else "PaymentDeclined"
        val event = PaymentResultEvent(
            eventId = UUID.randomUUID(),
            eventType = eventType,
            occurredAt = payment.updatedAt,
            data = PaymentResultData(
                paymentId = payment.id,
                orderId = payment.orderId,
                status = payment.status,
                amountMinor = payment.amountMinor,
                currency = payment.currency.trim(),
                reason = if (payment.status == PaymentStatus.DECLINED) "PAYMENT_METHOD_DECLINED" else null,
            ),
        )
        jdbcTemplate.update(
            """
            INSERT INTO outbox_events
                (id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
            VALUES (?, 'PAYMENT', ?, ?, CAST(? AS jsonb), ?)
            """.trimIndent(),
            event.eventId,
            payment.id,
            event.eventType,
            objectMapper.writeValueAsString(event),
            Timestamp.from(event.occurredAt),
        )
    }
}
