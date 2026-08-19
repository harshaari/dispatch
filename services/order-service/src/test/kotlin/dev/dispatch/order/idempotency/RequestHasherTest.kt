package dev.dispatch.order.idempotency

import tools.jackson.module.kotlin.jacksonObjectMapper
import dev.dispatch.order.api.CreateOrderItemRequest
import dev.dispatch.order.api.CreateOrderRequest
import dev.dispatch.order.api.DeliveryAddressRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequestHasherTest {
    private val hasher = RequestHasher(jacksonObjectMapper())

    @Test
    fun `hash is stable when request items arrive in a different order`() {
        val first = request(listOf(item("burger", 1), item("fries", 2)))
        val reordered = request(listOf(item("fries", 2), item("burger", 1)))

        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(reordered))
    }

    private fun request(items: List<CreateOrderItemRequest>) = CreateOrderRequest(
        customerId = "cust_123",
        merchantId = "merchant_456",
        items = items,
        currency = "USD",
        deliveryAddress = DeliveryAddressRequest(35.61, -78.74),
        paymentMethodId = "pm_123",
    )

    private fun item(sku: String, quantity: Int) = CreateOrderItemRequest(sku, quantity, 1299)
}
