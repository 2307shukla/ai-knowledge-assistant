package com.abhijeet.ai_knowledge_assistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * CSRF defence for the cookie-based session: browser requests that change state (POST, PUT, PATCH,
 * DELETE) must come from this site.
 *
 * <p>Modern browsers label every request: {@code Sec-Fetch-Site: cross-site} means another site
 * triggered it, and {@code Origin} names the page that did. Either one pointing elsewhere is
 * refused with 403 before any controller runs. Requests with neither header (curl, server-to-server
 * clients, tests) are not browser-driven cross-site requests and pass; they carry no ambient
 * cookie from a victim's browser. Together with the {@code SameSite=Lax} session cookie this
 * stops cross-site form posts and fetches without per-request tokens in the single-page UI.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CrossSiteRequestFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || sameSite(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":403,\"error\":\"Cross-site request blocked.\"}");
    }

    private static String firstValue(String header) {
        return header == null ? null : header.split(",")[0].trim();
    }

    /** "example.com:8080" becomes "example.com". */
    private static String hostOf(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return null;
        }
        if (hostHeader.startsWith("[")) {
            int end = hostHeader.indexOf(']');
            return end > 0 ? hostHeader.substring(0, end + 1) : hostHeader;
        }
        int colon = hostHeader.lastIndexOf(':');
        return colon > 0 ? hostHeader.substring(0, colon) : hostHeader;
    }

    static boolean sameSite(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && "cross-site".equals(fetchSite.toLowerCase(Locale.ROOT))) {
            return false;
        }
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            // No Origin: a same-origin navigation or a non-browser client. "null" (sandboxed or
            // redirected contexts) is treated as foreign only when a browser also sent Fetch metadata.
            return !"null".equals(origin) || fetchSite == null || "same-origin".equals(fetchSite);
        }
        try {
            String host = URI.create(origin).getHost();
            if (host == null) {
                return false;
            }
            // Host names only: behind a proxy the port the app sees can differ from the public one.
            return host.equalsIgnoreCase(request.getServerName())
                    || host.equalsIgnoreCase(hostOf(request.getHeader("Host")))
                    || host.equalsIgnoreCase(hostOf(firstValue(request.getHeader("X-Forwarded-Host"))));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
