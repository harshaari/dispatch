package dev.dispatch.order

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.ObjectMapper
import dev.dispatch.order.outbox.OutboxEventPublisher
import java.time.Duration
import java.util.Properties
import java.util.UUID

@Testcontainers
@SpringBootTest(properties = ["app.outbox.publisher.fixed-delay=PT1H"])
@AutoConfigureMockMvc
class OutboxKafkaIntegrationTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var outboxEventPublisher: OutboxEventPublisher

    @BeforeEach
    fun clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, idempotency_records, outbox_events, orders")
    }

    @Test
    fun `publishes a newly created order as an OrderCreated Kafka event`() {
        val createResult = mockMvc.perform(
            post("/api/v1/orders")
                .header("Idempotency-Key", "m2-outbox-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()),
        )
            .andExpect(status().isCreated)
            .andReturn()
        val orderId = objectMapper.readTree(createResult.response.contentAsString)["orderId"].asText()

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Idempotency-Key", "m2-outbox-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()),
        ).andExpect(status().isCreated)

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Long::class.java)).isEqualTo(1)

        KafkaConsumer<String, String>(consumerProperties()).use { consumer ->
            consumer.subscribe(listOf(TOPIC))
            outboxEventPublisher.publishAvailable()

            val record = consumer.poll(Duration.ofSeconds(10)).single()
            val event = objectMapper.readTree(record.value())

            assertThat(record.key()).isEqualTo(orderId)
            assertThat(event["eventType"].asText()).isEqualTo("OrderCreated")
            assertThat(event["schemaVersion"].asInt()).isEqualTo(1)
            assertThat(event["data"]["orderId"].asText()).isEqualTo(orderId)
            assertThat(event["data"]["status"].asText()).isEqualTo("PAYMENT_PENDING")
            assertThat(event["data"]["totalAmountMinor"].asLong()).isEqualTo(2598)
        }

        assertThat(
            jdbcTemplate.queryForObject("SELECT published_at IS NOT NULL FROM outbox_events", Boolean::class.java),
        ).isTrue()
    }

    private fun consumerProperties(): Properties = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-${UUID.randomUUID()}")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }

    private fun validRequest() =
        """
        {
          "customerId": "cust_123",
          "merchantId": "merchant_456",
          "items": [{"sku": "burger_001", "quantity": 2, "unitPriceMinor": 1299}],
          "currency": "USD",
          "deliveryAddress": {"latitude": 35.61, "longitude": -78.74},
          "paymentMethodId": "pm_123"
        }
        """.trimIndent()

    companion object {
        private const val TOPIC = "dispatch.order.events.v1"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("dispatch_outbox_test")
            .withUsername("dispatch")
            .withPassword("dispatch")

        @Container
        @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }
}
