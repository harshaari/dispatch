package dev.dispatch.order.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "order_items")
class OrderItemEntity(
    @Id val id: UUID,
    @ManyToOne(optional = false) @JoinColumn(name = "order_id") val order: OrderEntity,
    val sku: String,
    val quantity: Int,
    @Column(name = "unit_price_minor") val unitPriceMinor: Long,
    @Column(name = "created_at") val createdAt: Instant,
)
