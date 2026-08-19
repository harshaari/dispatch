package dev.dispatch.order.api

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val inbound = request.getHeader(HEADER)
        val requestId = inbound?.takeIf { VALID_ID.matches(it) } ?: UUID.randomUUID().toString()
        request.setAttribute(ATTRIBUTE, requestId)
        response.setHeader(HEADER, requestId)
        MDC.put(ATTRIBUTE, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(ATTRIBUTE)
        }
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val ATTRIBUTE = "requestId"
        private val VALID_ID = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
