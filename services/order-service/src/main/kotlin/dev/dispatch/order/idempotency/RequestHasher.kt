package dev.dispatch.order.idempotency

import tools.jackson.databind.ObjectMapper
import dev.dispatch.order.api.CreateOrderRequest
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class RequestHasher(objectMapper: ObjectMapper) {
    private val canonicalMapper = objectMapper

    fun hash(request: CreateOrderRequest): String {
        val canonical = linkedMapOf(
            "customerId" to request.customerId,
            "merchantId" to request.merchantId,
            "items" to request.items.sortedBy { it.sku }.map {
                linkedMapOf(
                    "sku" to it.sku,
                    "quantity" to it.quantity,
                    "unitPriceMinor" to it.unitPriceMinor,
                )
            },
            "currency" to request.currency,
            "deliveryAddress" to linkedMapOf(
                "latitude" to request.deliveryAddress.latitude,
                "longitude" to request.deliveryAddress.longitude,
            ),
            "paymentMethodId" to request.paymentMethodId,
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(canonicalMapper.writeValueAsBytes(canonical))
            .joinToString("") { "%02x".format(it) }
    }
}
