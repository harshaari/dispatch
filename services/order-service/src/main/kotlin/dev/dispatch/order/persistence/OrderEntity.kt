package dev.dispatch.order.persistence

import dev.dispatch.order.domain.OrderStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import java.time.Instant
import java.sql.Types
import java.util.UUID

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id val id: UUID,
    @Column(name = "customer_id") val customerId: String,
    @Column(name = "merchant_id") val merchantId: String,
    @Enumerated(EnumType.STRING) var status: OrderStatus,
    @Column(name = "total_amount_minor") val totalAmountMinor: Long,
    @JdbcTypeCode(Types.CHAR)
    @Column(columnDefinition = "char(3)") val currency: String,
    @Column(name = "delivery_latitude") val deliveryLatitude: Double,
    @Column(name = "delivery_longitude") val deliveryLongitude: Double,
    @Column(name = "payment_method_id") val paymentMethodId: String,
    @Column(name = "created_at") val createdAt: Instant,
    @Column(name = "updated_at") var updatedAt: Instant,
    @Version val version: Long = 0,
)
