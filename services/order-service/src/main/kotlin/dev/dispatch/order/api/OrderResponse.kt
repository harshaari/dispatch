package dev.dispatch.order.api

import dev.dispatch.order.domain.OrderStatus
import java.time.Instant
import java.util.UUID

data class OrderResponse(
    val orderId: UUID,
    val status: OrderStatus,
    val totalAmountMinor: Long,
    val currency: String,
    val createdAt: Instant,
)
