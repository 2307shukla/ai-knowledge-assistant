package com.abhijeet.ai_knowledge_assistant.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiting. Applies to the endpoints that call the AI provider or do heavy work
 * (per user, or per IP when anonymous). Sign-in and registration have their own earlier filter,
 * {@link AuthRateLimitFilter}, because Spring Security answers /login before this one runs.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 20;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!isLimited(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            evictIdleBuckets();
        }

        Bucket bucket = buckets.computeIfAbsent(clientKey(request), key -> newBucket(CAPACITY));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"Too many requests. Please wait a minute and try again.\"}");
    }

    private boolean isLimited(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/guest/chat")
                || path.equals("/api/rag/ask")
                || path.equals("/api/rag/upload")
                || path.endsWith("/reindex")
                || path.endsWith("/messages")
                || path.endsWith("/messages/stream");
    }

    private String clientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }

        // Tomcat's RemoteIpValve (server.forward-headers-strategy=native) resolves the
        // client address from X-Forwarded-For right to left, skipping only trusted
        // proxies, so a client cannot spoof it by sending its own header.
        return "ip:" + request.getRemoteAddr();
    }

    /**
     * Drops buckets that have fully refilled: those clients have been idle for at
     * least a refill period, so forgetting them loses no rate-limit state. Active
     * clients keep their buckets, so the cap can't be used to reset everyone's limit.
     */
    private void evictIdleBuckets() {
        buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= CAPACITY);
    }

    private Bucket newBucket(int capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, REFILL_PERIOD)
                        .build())
                .build();
    }
}
