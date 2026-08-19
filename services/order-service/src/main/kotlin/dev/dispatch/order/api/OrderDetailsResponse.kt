package dev.dispatch.order.api

import dev.dispatch.order.domain.OrderStatus
import java.time.Instant
import java.util.UUID

data class OrderDetailsResponse(
    val orderId: UUID,
    val customerId: String,
    val merchantId: String,
    val status: OrderStatus,
    val items: List<OrderItemResponse>,
    val totalAmountMinor: Long,
    val currency: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class OrderItemResponse(
    val sku: String,
    val quantity: Int,
    val unitPriceMinor: Long,
)

data class CancelOrderResponse(
    val orderId: UUID,
    val status: OrderStatus,
    val updatedAt: Instant,
)
