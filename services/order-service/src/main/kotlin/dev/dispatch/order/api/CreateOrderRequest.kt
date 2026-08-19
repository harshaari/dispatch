package dev.dispatch.order.api

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateOrderRequest(
    @field:NotBlank @field:Size(max = 64) val customerId: String,
    @field:NotBlank @field:Size(max = 64) val merchantId: String,
    @field:NotEmpty @field:Size(max = 100) @field:Valid val items: List<CreateOrderItemRequest>,
    @field:Pattern(regexp = "USD") val currency: String,
    @field:NotNull @field:Valid val deliveryAddress: DeliveryAddressRequest,
    @field:NotBlank @field:Size(max = 128) val paymentMethodId: String,
)

data class CreateOrderItemRequest(
    @field:NotBlank @field:Size(max = 128) val sku: String,
    @field:Min(1) val quantity: Int,
    @field:Min(0) val unitPriceMinor: Long,
)

data class DeliveryAddressRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: Double,
)
