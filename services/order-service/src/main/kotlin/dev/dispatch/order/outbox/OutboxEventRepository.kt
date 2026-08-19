package dev.dispatch.order.outbox

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ClaimedOutboxEvent(
    val id: UUID,
    val aggregateId: UUID,
    val eventType: String,
    val payload: String,
)

@Repository
class OutboxEventRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun append(
        id: UUID,
        aggregateId: UUID,
        eventType: String,
        payload: String,
        occurredAt: Instant,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO outbox_events
                (id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
            VALUES (?, 'ORDER', ?, ?, CAST(? AS jsonb), ?)
            """.trimIndent(),
            id,
            aggregateId,
            eventType,
            payload,
            Timestamp.from(occurredAt),
        )
    }

    @Transactional
    fun claimAvailable(relayId: UUID, batchSize: Int, leaseDuration: Duration): List<ClaimedOutboxEvent> {
        val now = Instant.now()
        val leaseUntil = now.plus(leaseDuration)
        return jdbcTemplate.query(
            """
            WITH candidates AS (
                SELECT id
                FROM outbox_events
                WHERE published_at IS NULL
                  AND (locked_until IS NULL OR locked_until < ?)
                ORDER BY occurred_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE outbox_events AS event
            SET locked_by = ?, locked_until = ?
            FROM candidates
            WHERE event.id = candidates.id
            RETURNING event.id, event.aggregate_id, event.event_type, event.payload::text AS payload
            """.trimIndent(),
            { rs, _ ->
                ClaimedOutboxEvent(
                    id = rs.getObject("id", UUID::class.java),
                    aggregateId = rs.getObject("aggregate_id", UUID::class.java),
                    eventType = rs.getString("event_type"),
                    payload = rs.getString("payload"),
                )
            },
            Timestamp.from(now),
            batchSize,
            relayId,
            Timestamp.from(leaseUntil),
        )
    }

    @Transactional
    fun markPublished(id: UUID, relayId: UUID, publishedAt: Instant = Instant.now()): Boolean =
        jdbcTemplate.update(
            """
            UPDATE outbox_events
            SET published_at = ?, locked_by = NULL, locked_until = NULL, last_error = NULL
            WHERE id = ? AND locked_by = ? AND published_at IS NULL
            """.trimIndent(),
            Timestamp.from(publishedAt),
            id,
            relayId,
        ) == 1

    @Transactional
    fun releaseForRetry(id: UUID, relayId: UUID, error: String): Boolean =
        jdbcTemplate.update(
            """
            UPDATE outbox_events
            SET attempts = attempts + 1, last_error = ?, locked_by = NULL, locked_until = NULL
            WHERE id = ? AND locked_by = ? AND published_at IS NULL
            """.trimIndent(),
            error.take(2_000),
            id,
            relayId,
        ) == 1
}
