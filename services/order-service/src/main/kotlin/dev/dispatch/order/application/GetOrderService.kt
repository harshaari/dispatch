package dev.dispatch.order.application

import dev.dispatch.order.api.OrderDetailsResponse
import dev.dispatch.order.api.OrderItemResponse
import dev.dispatch.order.error.OrderNotFoundException
import dev.dispatch.order.persistence.OrderItemRepository
import dev.dispatch.order.persistence.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetOrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
) {
    @Transactional(readOnly = true)
    fun get(orderId: UUID): OrderDetailsResponse {
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException(orderId.toString()) }
        val items = orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(orderId)
            .map { OrderItemResponse(it.sku, it.quantity, it.unitPriceMinor) }
        return OrderDetailsResponse(
            orderId = order.id,
            customerId = order.customerId,
            merchantId = order.merchantId,
            status = order.status,
            items = items,
            totalAmountMinor = order.totalAmountMinor,
            currency = order.currency.trim(),
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
    }
}
