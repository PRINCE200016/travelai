package com.tripmind.config;

import com.tripmind.repository.AuthSessionRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class BearerAuthFilter implements Filter {

    private final AuthSessionRepository authSessionRepository;

    public BearerAuthFilter(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Skip auth endpoints (they handle token issuance)
        if (path.startsWith("/api/auth")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                authSessionRepository.findByToken(token).ifPresent(session -> {
                    if (session.getExpiresAt() != null && session.getExpiresAt().isAfter(LocalDateTime.now())) {
                        httpRequest.setAttribute("userId", session.getUserId());
                    }
                });
            }
        }

        chain.doFilter(request, response);
    }
}

