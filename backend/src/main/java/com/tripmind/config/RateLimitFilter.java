package com.tripmind.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {

    // Simple in-memory rate limiter: IP -> request count
    private final Map<String, long[]> requestCounts = new ConcurrentHashMap<>();
    
    // Max 30 AI chat requests per minute per IP
    private static final int MAX_REQUESTS = 30;
    private static final long WINDOW_MS = 60_000;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        
        // Only rate limit AI chat endpoint
        if ("/api/chat".equals(path) && "POST".equalsIgnoreCase(httpRequest.getMethod())) {
            String ip = getClientIP(httpRequest);
            
            if (isRateLimited(ip)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        
        requestCounts.compute(ip, (key, val) -> {
            if (val == null || now - val[1] > WINDOW_MS) {
                return new long[]{1, now};
            }
            val[0]++;
            return val;
        });

        long[] counts = requestCounts.get(ip);
        return counts != null && counts[0] > MAX_REQUESTS;
    }

    private String getClientIP(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
