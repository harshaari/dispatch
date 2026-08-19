package dev.dispatch.order.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderItemRepository : JpaRepository<OrderItemEntity, UUID> {
    fun findAllByOrder_IdOrderByCreatedAtAsc(orderId: UUID): List<OrderItemEntity>
}
