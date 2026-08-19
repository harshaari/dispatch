package dev.dispatch.order.domain

import dev.dispatch.order.error.InvalidStateTransitionException

object OrderStateMachine {
    fun cancel(status: OrderStatus): OrderStatus {
        if (status !in cancellableStates) {
            throw InvalidStateTransitionException("Order cannot transition from $status to CANCELLED.")
        }
        return OrderStatus.CANCELLED
    }

    private val cancellableStates = setOf(
        OrderStatus.PAYMENT_PENDING,
        OrderStatus.PAYMENT_CONFIRMED,
        OrderStatus.DISPATCH_PENDING,
        OrderStatus.DRIVER_ASSIGNED,
    )
}
