package dev.dispatch.order.error

class IdempotencyConflictException : RuntimeException(
    "The idempotency key was previously used with a different request.",
)

class InvalidOrderRequestException(message: String) : RuntimeException(message)

class OrderNotFoundException(orderId: String) : RuntimeException("Order $orderId was not found.")

class InvalidStateTransitionException(message: String) : RuntimeException(message)
