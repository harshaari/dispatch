package dev.dispatch.order.api

import dev.dispatch.order.application.CancelOrderService
import dev.dispatch.order.application.CreateOrderService
import dev.dispatch.order.application.GetOrderService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val createOrderService: CreateOrderService,
    private val getOrderService: GetOrderService,
    private val cancelOrderService: CancelOrderService,
) {
    @PostMapping
    @Operation(summary = "Create an order")
    fun createOrder(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<OrderResponse> {
        val response = createOrderService.create(request, idempotencyKey)
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, URI.create("/api/v1/orders/${response.orderId}").toString())
            .body(response)
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get an order")
    fun getOrder(@PathVariable orderId: java.util.UUID): OrderDetailsResponse = getOrderService.get(orderId)

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order")
    fun cancelOrder(@PathVariable orderId: java.util.UUID): CancelOrderResponse = cancelOrderService.cancel(orderId)
}
