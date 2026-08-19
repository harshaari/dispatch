package dev.dispatch.dispatch

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/assignments")
class AssignmentController(private val jdbcTemplate: JdbcTemplate) {
    @PostMapping("/{assignmentId}/pickup")
    @Transactional
    fun pickup(@PathVariable assignmentId: UUID) = transition(assignmentId, "ASSIGNED", "PICKED_UP")

    @PostMapping("/{assignmentId}/deliver")
    @Transactional
    fun deliver(@PathVariable assignmentId: UUID): AssignmentStatus {
        val result = transition(assignmentId, "PICKED_UP", "DELIVERED")
        jdbcTemplate.update("UPDATE drivers SET status = 'AVAILABLE' WHERE id = (SELECT driver_id FROM assignments WHERE id = ?)", assignmentId)
        return result
    }

    private fun transition(id: UUID, expected: String, next: String): AssignmentStatus {
        val updated = jdbcTemplate.update("UPDATE assignments SET status = ? WHERE id = ? AND status = ?", next, id, expected)
        if (updated != 1) throw IllegalStateException("Assignment cannot transition to $next")
        return AssignmentStatus(id, next)
    }
}

data class AssignmentStatus(val assignmentId: UUID, val status: String)
