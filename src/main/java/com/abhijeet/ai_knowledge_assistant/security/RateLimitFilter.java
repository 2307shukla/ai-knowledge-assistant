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
 * Token-bucket rate limiting. Two limits: the endpoints that call the AI provider or do heavy work
 * (per user, or per IP when anonymous), and sign-in / registration (per IP, stricter, to slow down
 * password guessing and account spam).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 20;
    private static final int AUTH_CAPACITY = 10;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        boolean auth = isAuthAttempt(request);
        if (!auth && !isLimited(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            evictIdleBuckets();
        }
        if (authBuckets.size() > MAX_TRACKED_CLIENTS) {
            authBuckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= AUTH_CAPACITY);
        }

        Bucket bucket = auth
                ? authBuckets.computeIfAbsent("ip:" + request.getRemoteAddr(), key -> newBucket(AUTH_CAPACITY))
                : buckets.computeIfAbsent(clientKey(request), key -> newBucket(CAPACITY));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"Too many requests. Please wait a minute and try again.\"}");
    }

    /** Password sign-in and registration: POST only, counted per client IP. */
    private boolean isAuthAttempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "POST".equals(request.getMethod())
                && (path.equals("/login") || path.equals("/api/auth/register"));
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
