package dev.dispatch.order.payment

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import dev.dispatch.order.domain.OrderStateMachine
import dev.dispatch.order.persistence.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Component
class PaymentResultListener(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val orderRepository: OrderRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${app.payment.result-topic}"], groupId = "\${app.payment.consumer-group}")
    @Transactional
    fun process(payload: String) {
        val root = objectMapper.readTree(payload)
        val eventType = root.requiredText("eventType")
        if (eventType !in setOf("PaymentAuthorized", "PaymentDeclined")) return
        val eventId = UUID.fromString(root.requiredText("eventId"))
        val inserted = jdbcTemplate.update(
            "INSERT INTO processed_inbound_events (event_id, event_type, processed_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            eventId, eventType, Timestamp.from(Instant.now()),
        ) == 1
        if (!inserted) return

        val orderId = UUID.fromString(root.required("data").requiredText("orderId"))
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        val nextStatus = OrderStateMachine.applyPaymentResult(order.status, eventType == "PaymentAuthorized") ?: return
        order.status = nextStatus
        order.updatedAt = Instant.now()
        orderRepository.saveAndFlush(order)
        logger.atInfo().addKeyValue("orderId", orderId).addKeyValue("paymentEventId", eventId)
            .addKeyValue("status", nextStatus).log("Payment result applied to order")
    }

    private fun JsonNode.requiredText(field: String): String = required(field).asString()
}
