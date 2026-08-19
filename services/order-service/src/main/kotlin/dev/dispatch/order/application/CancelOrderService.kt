package dev.dispatch.order.application

import dev.dispatch.order.api.CancelOrderResponse
import dev.dispatch.order.domain.OrderStateMachine
import dev.dispatch.order.error.OrderNotFoundException
import dev.dispatch.order.persistence.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CancelOrderService(private val orderRepository: OrderRepository) {
    @Transactional
    fun cancel(orderId: UUID): CancelOrderResponse {
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException(orderId.toString()) }
        order.status = OrderStateMachine.cancel(order.status)
        order.updatedAt = Instant.now()
        orderRepository.saveAndFlush(order)
        return CancelOrderResponse(order.id, order.status, order.updatedAt)
    }
}
