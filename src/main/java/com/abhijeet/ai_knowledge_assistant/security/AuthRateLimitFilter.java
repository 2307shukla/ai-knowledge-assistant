package com.abhijeet.ai_knowledge_assistant.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing and account spam: sign-in and registration are limited to 10 POSTs
 * a minute per client IP. It runs before Spring Security (which answers /login itself), so it sits
 * early in the filter chain, unlike {@link RateLimitFilter}, which needs the signed-in user.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 10;
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!isAuthAttempt(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= CAPACITY);
        }
        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), key -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(CAPACITY).refillGreedy(CAPACITY, REFILL_PERIOD).build())
                .build());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":429,\"error\":\"Too many sign-in attempts. Please wait a minute and try again.\"}");
    }

    private boolean isAuthAttempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "POST".equals(request.getMethod())
                && ("/login".equals(path) || "/api/auth/register".equals(path));
    }
}
