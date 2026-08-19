package dev.dispatch.order.domain

import dev.dispatch.order.error.InvalidStateTransitionException

object OrderStateMachine {
    fun applyPaymentResult(status: OrderStatus, authorized: Boolean): OrderStatus? {
        if (status != OrderStatus.PAYMENT_PENDING) return null
        return if (authorized) OrderStatus.PAYMENT_CONFIRMED else OrderStatus.PAYMENT_FAILED
    }

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
