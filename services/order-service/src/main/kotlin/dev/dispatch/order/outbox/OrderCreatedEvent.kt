package dev.dispatch.order.outbox

import dev.dispatch.order.domain.OrderStatus
import java.time.Instant
import java.util.UUID

data class OrderCreatedEvent(
    val eventId: UUID,
    val eventType: String = "OrderCreated",
    val schemaVersion: Int = 1,
    val occurredAt: Instant,
    val data: OrderCreatedData,
)

data class OrderCreatedData(
    val orderId: UUID,
    val customerId: String,
    val merchantId: String,
    val status: OrderStatus,
    val totalAmountMinor: Long,
    val currency: String,
)
