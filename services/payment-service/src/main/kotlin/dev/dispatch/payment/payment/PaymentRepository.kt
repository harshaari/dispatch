package dev.dispatch.payment.payment

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentRepository : JpaRepository<PaymentEntity, UUID>
