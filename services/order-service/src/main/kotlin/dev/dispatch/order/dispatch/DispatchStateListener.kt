package dev.dispatch.order.dispatch

import dev.dispatch.order.domain.OrderStatus
import dev.dispatch.order.persistence.OrderRepository
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Component
class DispatchStateListener(private val objectMapper: ObjectMapper, private val orders: OrderRepository) {
    @KafkaListener(topics = ["\${app.dispatch.event-topic}"], groupId = "\${app.dispatch.consumer-group}")
    @Transactional fun process(payload: String) {
        val root = objectMapper.readTree(payload); val type = root.required("eventType").asString()
        val order = orders.findById(UUID.fromString(root.required("data").required("orderId").asString())).orElse(null) ?: return
        val next = when (type) { "DriverAssigned" -> OrderStatus.DRIVER_ASSIGNED; "OrderPickedUp" -> OrderStatus.PICKED_UP; "OrderDelivered" -> OrderStatus.DELIVERED; else -> return }
        if (order.status.ordinal < next.ordinal) { order.status = next; order.updatedAt = Instant.now(); orders.saveAndFlush(order) }
    }
}
