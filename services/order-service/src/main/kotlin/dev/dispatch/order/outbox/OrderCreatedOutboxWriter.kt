package dev.dispatch.order.outbox

import tools.jackson.databind.ObjectMapper
import dev.dispatch.order.persistence.OrderEntity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class OrderCreatedOutboxWriter(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) {
    fun append(order: OrderEntity, occurredAt: Instant) {
        val event = OrderCreatedEvent(
            eventId = UUID.randomUUID(),
            occurredAt = occurredAt,
            data = OrderCreatedData(
                orderId = order.id,
                customerId = order.customerId,
                merchantId = order.merchantId,
                status = order.status,
                totalAmountMinor = order.totalAmountMinor,
                currency = order.currency.trim(),
            ),
        )
        outboxEventRepository.append(
            id = event.eventId,
            aggregateId = order.id,
            eventType = event.eventType,
            payload = objectMapper.writeValueAsString(event),
            occurredAt = occurredAt,
        )
    }
}
