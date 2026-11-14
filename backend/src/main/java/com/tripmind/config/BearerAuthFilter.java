package com.tripmind.config;

import com.tripmind.repository.AuthSessionRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/** Resolves "Authorization: Bearer token" to a userId request attribute. Never blocks a request. */
@Component
@Order(2)
public class BearerAuthFilter implements Filter {

    private final AuthSessionRepository sessions;

    public BearerAuthFilter(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String auth = http.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && http.getRequestURI().startsWith("/api/")) {
            String token = auth.substring(7).trim();
            if (!token.isEmpty() && token.length() <= 200) {
                sessions.findByToken(Tokens.hash(token)).ifPresent(s -> {
                    if (s.getExpiresAt() != null && s.getExpiresAt().isAfter(LocalDateTime.now())) {
                        http.setAttribute("userId", s.getUserId());
                    }
                });
            }
        }
        chain.doFilter(request, response);
    }
}
