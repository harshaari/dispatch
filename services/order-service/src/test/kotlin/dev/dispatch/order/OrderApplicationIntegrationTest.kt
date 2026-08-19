package dev.dispatch.order

import tools.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderApplicationIntegrationTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, idempotency_records, orders")
    }

    @Test
    fun `application starts and Flyway creates the M1 tables`() {
        val tables = jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
            """.trimIndent(),
            String::class.java,
        )

        assertThat(tables).contains("orders", "order_items", "idempotency_records", "flyway_schema_history")
    }

    @Test
    fun `creates an order and replays the original response for the same key`() {
        val first = mockMvc.perform(post("/api/v1/orders")
            .header("Idempotency-Key", "create-order-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequest()))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/orders/.+")))
            .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
            .andExpect(jsonPath("$.totalAmountMinor").value(2598))
            .andReturn()

        val firstBody = first.response.contentAsString
        val orderId = objectMapper.readTree(firstBody)["orderId"].asText()
        val replay = mockMvc.perform(post("/api/v1/orders")
            .header("Idempotency-Key", "create-order-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequest()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andReturn()

        assertThat(replay.response.contentAsString).isEqualTo(firstBody)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long::class.java)).isEqualTo(1)
    }

    @Test
    fun `rejects an idempotency key reused for a different request`() {
        mockMvc.perform(post("/api/v1/orders")
            .header("Idempotency-Key", "conflict-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequest()))
            .andExpect(status().isCreated)

        mockMvc.perform(post("/api/v1/orders")
            .header("Idempotency-Key", "conflict-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequest(quantity = 3)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `returns a structured validation error when idempotency key is missing`() {
        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequest()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty)
    }

    @Test
    fun `retrieves and cancels a pending order`() {
        val createResult = mockMvc.perform(post("/api/v1/orders")
            .header("Idempotency-Key", "get-cancel-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequest()))
            .andExpect(status().isCreated)
            .andReturn()
        val orderId = objectMapper.readTree(createResult.response.contentAsString)["orderId"].asText()

        mockMvc.perform(get("/api/v1/orders/$orderId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("cust_123"))
            .andExpect(jsonPath("$.items[0].sku").value("burger_001"))

        mockMvc.perform(post("/api/v1/orders/$orderId/cancel"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))

        mockMvc.perform(get("/api/v1/orders/$orderId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
    }

    @Test
    fun `concurrent retries create exactly one order`() {
        val workers = 16
        val executor = Executors.newFixedThreadPool(workers)
        val start = CountDownLatch(1)
        try {
            val results = (1..workers).map {
                executor.submit<Pair<Int, String>> {
                    start.await()
                    val result = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "concurrent-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                        .andReturn()
                    result.response.status to result.response.contentAsString
                }
            }
            start.countDown()
            val responses = results.map { it.get(20, TimeUnit.SECONDS) }

            assertThat(responses.map { it.first }).containsOnly(201)
            assertThat(responses.map { it.second }.toSet()).hasSize(1)
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long::class.java)).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun validRequest(quantity: Int = 2) =
        """
        {
          "customerId": "cust_123",
          "merchantId": "merchant_456",
          "items": [{"sku": "burger_001", "quantity": $quantity, "unitPriceMinor": 1299}],
          "currency": "USD",
          "deliveryAddress": {"latitude": 35.61, "longitude": -78.74},
          "paymentMethodId": "pm_123"
        }
        """.trimIndent()

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("dispatch_test")
            .withUsername("dispatch")
            .withPassword("dispatch")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
