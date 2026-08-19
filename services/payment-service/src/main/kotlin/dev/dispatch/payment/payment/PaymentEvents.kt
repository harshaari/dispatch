package dev.dispatch.payment.payment

import java.time.Instant
import java.util.UUID

data class PaymentResultEvent(
    val eventId: UUID,
    val eventType: String,
    val schemaVersion: Int = 1,
    val occurredAt: Instant,
    val data: PaymentResultData,
)

data class PaymentResultData(
    val paymentId: UUID,
    val orderId: UUID,
    val status: PaymentStatus,
    val amountMinor: Long,
    val currency: String,
    val reason: String? = null,
)
