package com.tripmind.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** One line per API call with status and duration. No bodies, headers or IPs are logged. */
@Component
@Order(0)
public class RequestLoggingFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        long t0 = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (http.getRequestURI().startsWith("/api/")) {
                log.info("{} {} -> {} ({} ms)", http.getMethod(), http.getRequestURI(),
                        ((HttpServletResponse) response).getStatus(), (System.nanoTime() - t0) / 1_000_000);
            }
        }
    }
}
