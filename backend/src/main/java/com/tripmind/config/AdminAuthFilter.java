package com.tripmind.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** HTTP Basic auth for every /api/admin/** route. No WWW-Authenticate header, so browsers don't pop a native dialog. */
@Component
@Order(3)
public class AdminAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);
    static final String DEFAULT_PASSWORD = "change_this_in_production";

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:" + DEFAULT_PASSWORD + "}")
    private String adminPassword;

    @PostConstruct
    void warnOnDefault() {
        if (adminPassword == null || DEFAULT_PASSWORD.equals(adminPassword) || adminPassword.length() < 12) {
            log.warn("ADMIN_PASS is the default or shorter than 12 characters. Set a strong ADMIN_PASS before deploying.");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        if (!http.getRequestURI().startsWith("/api/admin") || "OPTIONS".equalsIgnoreCase(http.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (!authorised(http.getHeader("Authorization"))) {
            HttpServletResponse res = (HttpServletResponse) response;
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Admin sign-in required\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean authorised(String header) {
        if (header == null || !header.startsWith("Basic ")) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int i = decoded.indexOf(':');
            if (i < 0) return false;
            boolean userOk = Tokens.safeEquals(decoded.substring(0, i), adminUsername);
            boolean passOk = Tokens.safeEquals(decoded.substring(i + 1), adminPassword);
            return userOk & passOk;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
