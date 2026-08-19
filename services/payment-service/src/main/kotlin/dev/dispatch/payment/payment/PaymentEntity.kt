package dev.dispatch.payment.payment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Types
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode

@Entity
@Table(name = "payments")
class PaymentEntity(
    @Id val id: UUID,
    @Column(name = "order_id") val orderId: UUID,
    @Enumerated(EnumType.STRING) val status: PaymentStatus,
    @Column(name = "amount_minor") val amountMinor: Long,
    @JdbcTypeCode(Types.CHAR)
    @Column(columnDefinition = "char(3)") val currency: String,
    @Column(name = "created_at") val createdAt: Instant,
    @Column(name = "updated_at") val updatedAt: Instant,
)
