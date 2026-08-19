package dev.dispatch.dispatch

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Component
class PaymentAuthorizedListener(private val jdbcTemplate: JdbcTemplate, private val objectMapper: ObjectMapper) {
    @KafkaListener(topics = ["\${app.dispatch.payment-topic}"], groupId = "\${app.dispatch.consumer-group}")
    @Transactional
    fun assign(payload: String) {
        val root = objectMapper.readTree(payload)
        if (root.required("eventType").asString() != "PaymentAuthorized") return
        val eventId = UUID.fromString(root.required("eventId").asString())
        if (jdbcTemplate.update("INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?) ON CONFLICT DO NOTHING", eventId, Timestamp.from(Instant.now())) != 1) return
        val driverId = jdbcTemplate.query(
            "SELECT id FROM drivers WHERE status = 'AVAILABLE' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
        ).firstOrNull() ?: error("No driver is currently available")
        val orderId = UUID.fromString(root.required("data").required("orderId").asString())
        jdbcTemplate.update("UPDATE drivers SET status = 'ASSIGNED' WHERE id = ?", driverId)
        jdbcTemplate.update("INSERT INTO assignments (id, order_id, driver_id, created_at) VALUES (?, ?, ?, ?)", UUID.randomUUID(), orderId, driverId, Timestamp.from(Instant.now()))
    }
}
