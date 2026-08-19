package dev.dispatch.payment.config

import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
class KafkaFailureConfiguration {
    @Bean
    @Suppress("UNCHECKED_CAST")
    fun kafkaErrorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate as KafkaOperations<Any, Any>) { record, _ ->
            TopicPartition("${record.topic()}.DLT", record.partition())
        }
        return DefaultErrorHandler(recoverer, FixedBackOff(1_000L, 2L))
    }
}
