package dev.dispatch.payment.payment

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Component
class OrderCreatedListener(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val paymentRepository: PaymentRepository,
    private val paymentOutboxWriter: PaymentOutboxWriter,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${app.payment.order-topic}"], groupId = "\${app.payment.consumer-group}")
    @Transactional
    fun process(payload: String) {
        val root = objectMapper.readTree(payload)
        if (root.requiredText("eventType") != "OrderCreated") return
        val eventId = UUID.fromString(root.requiredText("eventId"))
        val inserted = jdbcTemplate.update(
            "INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, 'OrderCreated', ?) ON CONFLICT DO NOTHING",
            eventId,
            Timestamp.from(Instant.now()),
        ) == 1
        if (!inserted) return

        val data = root.required("data")
        val status = if (data.requiredText("merchantId").startsWith("merchant_decline_")) {
            PaymentStatus.DECLINED
        } else {
            PaymentStatus.AUTHORIZED
        }
        val now = Instant.now()
        val payment = paymentRepository.save(
            PaymentEntity(
                id = UUID.randomUUID(),
                orderId = UUID.fromString(data.requiredText("orderId")),
                status = status,
                amountMinor = data.requiredLong("totalAmountMinor"),
                currency = data.requiredText("currency"),
                createdAt = now,
                updatedAt = now,
            ),
        )
        paymentOutboxWriter.appendResult(payment)
        logger.atInfo().addKeyValue("orderId", payment.orderId).addKeyValue("paymentId", payment.id)
            .addKeyValue("status", payment.status).log("Payment decision recorded")
    }

    private fun JsonNode.requiredText(field: String): String = required(field).asString()
    private fun JsonNode.requiredLong(field: String): Long = required(field).longValue()
}
