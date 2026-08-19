package dev.dispatch.dispatch

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/drivers")
class DriverController(private val jdbcTemplate: JdbcTemplate) {
    @GetMapping fun list() = jdbcTemplate.query("SELECT id, status FROM drivers ORDER BY id") { rs, _ -> DriverStatus(rs.getObject("id", UUID::class.java), rs.getString("status")) }
    @PostMapping("/{driverId}/available") fun available(@PathVariable driverId: UUID) = update(driverId, "AVAILABLE")
    @PostMapping("/{driverId}/unavailable") fun unavailable(@PathVariable driverId: UUID) = update(driverId, "UNAVAILABLE")
    private fun update(id: UUID, status: String): DriverStatus {
        if (jdbcTemplate.update("UPDATE drivers SET status = ? WHERE id = ? AND status <> 'ASSIGNED'", status, id) != 1) throw IllegalStateException("Driver is not available for this operation")
        return DriverStatus(id, status)
    }
}
data class DriverStatus(val driverId: UUID, val status: String)
