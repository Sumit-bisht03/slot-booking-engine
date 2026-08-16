package com.hackathon.slot_booking_engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.slot_booking_engine.dto.ApiResponse;
import com.hackathon.slot_booking_engine.service.RateLimiterService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. Resolve client identity: Prefer X-User-Id header, fall back to remote IP address
        String userIdHeader = request.getHeader("X-User-Id");
        String clientKey;

        if (userIdHeader != null && !userIdHeader.trim().isEmpty()) {
            clientKey = "rate_limit_user_" + userIdHeader.trim();
        } else {
            String ipAddress = request.getRemoteAddr();
            clientKey = "rate_limit_ip_" + ipAddress;
        }

        // 2. Resolve user bucket and consume 1 token
        Bucket bucket = rateLimiterService.resolveBucket(clientKey);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Token available: attach remaining count header and proceed to controller
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        // 3. Bucket empty: Reject with HTTP 429 Too Many Requests
        long waitForRefillSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
        log.warn("Rate limit exceeded for clientKey='{}'. Retry after {}s", clientKey, waitForRefillSeconds);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-RateLimit-Retry-After-Seconds", String.valueOf(waitForRefillSeconds));

        ApiResponse<Void> errorResponse = ApiResponse.error(
                "Rate limit exceeded. Maximum 5 booking attempts per minute allowed. Try again in "
                        + waitForRefillSeconds + " seconds."
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        return false; // Stop further handling in Spring execution chain
    }
}