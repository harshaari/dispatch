package dev.dispatch.order.api

import dev.dispatch.order.error.IdempotencyConflictException
import dev.dispatch.order.error.InvalidOrderRequestException
import dev.dispatch.order.error.InvalidStateTransitionException
import dev.dispatch.order.error.OrderNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.Instant

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class, MissingRequestHeaderException::class, InvalidOrderRequestException::class)
    fun badRequest(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.message ?: "The request is invalid.", request)

    @ExceptionHandler(IdempotencyConflictException::class)
    fun idempotencyConflict(exception: IdempotencyConflictException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", exception.message ?: "Idempotency conflict.", request)

    @ExceptionHandler(InvalidStateTransitionException::class, ObjectOptimisticLockingFailureException::class)
    fun stateConflict(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", exception.message ?: "Order state changed concurrently.", request)

    @ExceptionHandler(OrderNotFoundException::class)
    fun notFound(exception: OrderNotFoundException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.message ?: "Order was not found.", request)

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = ResponseEntity.status(status).body(
        ApiErrorResponse(
            code = code,
            message = message,
            requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE) as String,
            timestamp = Instant.now(),
        ),
    )
}

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val requestId: String,
    val timestamp: Instant,
)
