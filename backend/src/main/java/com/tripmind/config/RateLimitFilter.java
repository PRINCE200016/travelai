package com.tripmind.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fixed-window, per-IP limits on endpoints that cost compute or accept writes. Memory is bounded. */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 50_000;

    private final Map<String, long[]> counters = new ConcurrentHashMap<>();
    private volatile long lastSweep = System.currentTimeMillis();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        int limit = limitFor(http.getMethod(), http.getRequestURI());
        if (limit > 0) {
            String key = clientIp(http) + "|" + bucket(http.getRequestURI());
            if (exceeded(key, limit)) {
                HttpServletResponse res = (HttpServletResponse) response;
                res.setStatus(429);
                res.setHeader("Retry-After", "60");
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Too many requests. Please wait a minute and try again.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    static int limitFor(String method, String path) {
        if (path.startsWith("/api/admin")) return 120;
        if (!"POST".equalsIgnoreCase(method)) return 0;
        if (path.equals("/api/plan")) return 15;
        if (path.startsWith("/api/auth/")) return 10;
        if (path.equals("/api/contact") || path.equals("/api/itinerary-request") || path.equals("/api/leads")) return 5;
        return 0;
    }

    private static String bucket(String path) {
        return path.startsWith("/api/auth/") ? "auth" : path.startsWith("/api/admin") ? "admin" : path;
    }

    private boolean exceeded(String key, int limit) {
        long now = System.currentTimeMillis();
        sweep(now);
        long[] c = counters.compute(key, (k, v) -> {
            if (v == null || now - v[1] > WINDOW_MS) return new long[]{1, now};
            v[0]++;
            return v;
        });
        return c[0] > limit;
    }

    private void sweep(long now) {
        if (now - lastSweep < WINDOW_MS && counters.size() < MAX_KEYS) return;
        lastSweep = now;
        counters.entrySet().removeIf(e -> now - e.getValue()[1] > WINDOW_MS);
    }

    private static String clientIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return r.getRemoteAddr();
    }
}
