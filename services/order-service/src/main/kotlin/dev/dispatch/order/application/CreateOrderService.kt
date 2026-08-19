package dev.dispatch.order.application

import tools.jackson.databind.ObjectMapper
import dev.dispatch.order.api.CreateOrderRequest
import dev.dispatch.order.api.OrderResponse
import dev.dispatch.order.domain.OrderStatus
import dev.dispatch.order.error.IdempotencyConflictException
import dev.dispatch.order.error.InvalidOrderRequestException
import dev.dispatch.order.idempotency.RequestHasher
import dev.dispatch.order.persistence.OrderEntity
import dev.dispatch.order.persistence.OrderItemEntity
import dev.dispatch.order.persistence.OrderItemRepository
import dev.dispatch.order.persistence.OrderRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class CreateOrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val requestHasher: RequestHasher,
) {
    @Transactional
    fun create(request: CreateOrderRequest, idempotencyKey: String): OrderResponse {
        if (idempotencyKey.isBlank() || idempotencyKey.length > 128) {
            throw InvalidOrderRequestException("Idempotency-Key must be between 1 and 128 characters.")
        }
        validateBusinessRules(request)
        val requestHash = requestHasher.hash(request)
        val now = Instant.now()
        val recordId = UUID.randomUUID()
        val inserted = jdbcTemplate.update(
            """
            INSERT INTO idempotency_records
                (id, operation, idempotency_key, request_hash, created_at, expires_at)
            VALUES (?, 'CREATE_ORDER', ?, ?, ?, ?)
            ON CONFLICT (operation, idempotency_key) DO NOTHING
            """.trimIndent(),
            recordId,
            idempotencyKey,
            requestHash,
            Timestamp.from(now),
            Timestamp.from(now.plus(Duration.ofHours(48))),
        ) == 1

        if (!inserted) return replayOrReject(idempotencyKey, requestHash)

        val total = calculateTotal(request)
        val order = orderRepository.save(
            OrderEntity(
                id = UUID.randomUUID(),
                customerId = request.customerId,
                merchantId = request.merchantId,
                status = OrderStatus.PAYMENT_PENDING,
                totalAmountMinor = total,
                currency = request.currency,
                deliveryLatitude = request.deliveryAddress.latitude,
                deliveryLongitude = request.deliveryAddress.longitude,
                paymentMethodId = request.paymentMethodId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        orderItemRepository.saveAll(request.items.map {
            OrderItemEntity(
                id = UUID.randomUUID(),
                order = order,
                sku = it.sku,
                quantity = it.quantity,
                unitPriceMinor = it.unitPriceMinor,
                createdAt = now,
            )
        })
        val response = OrderResponse(order.id, order.status, total, order.currency, order.createdAt)
        jdbcTemplate.update(
            """
            UPDATE idempotency_records
            SET resource_id = ?, response_status = 201, response_body = CAST(? AS jsonb)
            WHERE id = ?
            """.trimIndent(),
            order.id,
            objectMapper.writeValueAsString(response),
            recordId,
        )
        return response
    }

    private fun replayOrReject(idempotencyKey: String, requestHash: String): OrderResponse {
        val record = jdbcTemplate.queryForMap(
            """
            SELECT request_hash, response_body::text AS response_body
            FROM idempotency_records
            WHERE operation = 'CREATE_ORDER' AND idempotency_key = ?
            """.trimIndent(),
            idempotencyKey,
        )
        if (record["request_hash"] != requestHash) throw IdempotencyConflictException()
        val body = record["response_body"] as? String
            ?: error("A committed idempotency record must contain a response body.")
        return objectMapper.readValue(body, OrderResponse::class.java)
    }

    private fun validateBusinessRules(request: CreateOrderRequest) {
        if (request.items.map { it.sku }.toSet().size != request.items.size) {
            throw InvalidOrderRequestException("Items must not contain duplicate SKUs.")
        }
        try {
            request.items.forEach { Math.multiplyExact(it.unitPriceMinor, it.quantity.toLong()) }
        } catch (_: ArithmeticException) {
            throw InvalidOrderRequestException("Order total is too large.")
        }
    }

    private fun calculateTotal(request: CreateOrderRequest): Long = try {
        request.items.fold(0L) { total, item ->
            Math.addExact(total, Math.multiplyExact(item.unitPriceMinor, item.quantity.toLong()))
        }
    } catch (_: ArithmeticException) {
        throw InvalidOrderRequestException("Order total is too large.")
    }
}
