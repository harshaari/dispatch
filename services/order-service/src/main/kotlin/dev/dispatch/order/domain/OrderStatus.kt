package dev.dispatch.order.domain

enum class OrderStatus {
    PAYMENT_PENDING,
    PAYMENT_CONFIRMED,
    PAYMENT_FAILED,
    DISPATCH_PENDING,
    DRIVER_ASSIGNED,
    PICKED_UP,
    DELIVERED,
    CANCELLED,
}
