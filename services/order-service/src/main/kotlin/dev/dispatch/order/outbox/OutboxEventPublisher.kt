package dev.dispatch.order.outbox

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(prefix = "app.outbox.publisher", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${app.outbox.topic}") private val topic: String,
    @Value("\${app.outbox.publisher.batch-size}") private val batchSize: Int,
    @Value("\${app.outbox.publisher.lease-duration}") private val leaseDuration: Duration,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val relayId = UUID.randomUUID()

    @Scheduled(fixedDelayString = "\${app.outbox.publisher.fixed-delay}")
    fun publishAvailable() {
        outboxEventRepository.claimAvailable(relayId, batchSize, leaseDuration).forEach { event ->
            try {
                kafkaTemplate.send(topic, event.aggregateId.toString(), event.payload).get(10, TimeUnit.SECONDS)
                outboxEventRepository.markPublished(event.id, relayId)
                logger.atInfo()
                    .addKeyValue("eventId", event.id)
                    .addKeyValue("eventType", event.eventType)
                    .addKeyValue("aggregateId", event.aggregateId)
                    .log("Outbox event published")
            } catch (exception: Exception) {
                outboxEventRepository.releaseForRetry(event.id, relayId, exception.message ?: exception.javaClass.simpleName)
                logger.atWarn()
                    .setCause(exception)
                    .addKeyValue("eventId", event.id)
                    .addKeyValue("eventType", event.eventType)
                    .log("Outbox event publication failed; it will be retried")
            }
        }
    }
}
