package dev.dispatch.dispatch

import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class DispatchEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${app.dispatch.event-topic:dispatch.dispatch.events.v1}") private val topic: String,
) {
    fun publish(eventType: String, orderId: UUID, assignmentId: UUID) {
        val payload = linkedMapOf(
            "eventId" to UUID.randomUUID(),
            "eventType" to eventType,
            "schemaVersion" to 1,
            "occurredAt" to Instant.now(),
            "data" to linkedMapOf("orderId" to orderId, "assignmentId" to assignmentId),
        )
        kafkaTemplate.send(topic, orderId.toString(), objectMapper.writeValueAsString(payload))
    }
}
