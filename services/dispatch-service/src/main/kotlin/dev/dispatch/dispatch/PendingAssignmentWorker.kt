package dev.dispatch.dispatch

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class PendingAssignmentWorker(private val jdbcTemplate: JdbcTemplate) {
    @Scheduled(fixedDelayString = "\${app.dispatch.pending-retry-delay:1000}")
    @Transactional
    fun retry() {
        val orderId = jdbcTemplate.query("SELECT order_id FROM pending_assignments ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1", { rs, _ -> rs.getObject("order_id", UUID::class.java) }).firstOrNull() ?: return
        val driverId = jdbcTemplate.query("SELECT id FROM drivers WHERE status = 'AVAILABLE' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1", { rs, _ -> rs.getObject("id", UUID::class.java) }).firstOrNull() ?: return
        jdbcTemplate.update("UPDATE drivers SET status = 'ASSIGNED' WHERE id = ?", driverId)
        jdbcTemplate.update("INSERT INTO assignments (id, order_id, driver_id, created_at) VALUES (?, ?, ?, now())", UUID.randomUUID(), orderId, driverId)
        jdbcTemplate.update("DELETE FROM pending_assignments WHERE order_id = ?", orderId)
    }
}
