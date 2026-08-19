package dev.dispatch.dispatch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers
@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false", "app.dispatch.events.enabled=false"])
class DispatchConcurrencyIntegrationTest {
    @Autowired lateinit var listener: PaymentAuthorizedListener
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach fun reset() {
        jdbcTemplate.execute("TRUNCATE assignments, processed_events")
        jdbcTemplate.execute("UPDATE drivers SET status = 'AVAILABLE'")
    }

    @Test fun `concurrent payment events do not double assign drivers`() {
        val executor = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        try {
            val results = (1..3).map { index -> executor.submit<Boolean> {
                start.await()
                runCatching { listener.assign(event(index)) }.isSuccess
            } }
            start.countDown()
            results.forEach { it.get(10, TimeUnit.SECONDS) }
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignments", Long::class.java)).isEqualTo(2)
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM drivers WHERE status = 'ASSIGNED'", Long::class.java)).isEqualTo(2)
        } finally { executor.shutdownNow() }
    }

    private fun event(index: Int) = """{"eventId":"${UUID.randomUUID()}","eventType":"PaymentAuthorized","data":{"orderId":"${UUID.randomUUID()}"}}"""
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:17-alpine").withDatabaseName("dispatch_test").withUsername("dispatch").withPassword("dispatch")
        @DynamicPropertySource @JvmStatic fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl); registry.add("spring.datasource.username", postgres::getUsername); registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
