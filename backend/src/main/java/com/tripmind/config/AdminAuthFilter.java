package com.tripmind.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

@Component
public class AdminAuthFilter implements Filter {

    @Value("${admin.password:admin123}")
    private String adminPassword;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Only protect admin endpoints
        if (path.startsWith("/api/admin")) {
            String authHeader = httpRequest.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                sendUnauthorized((HttpServletResponse) response);
                return;
            }

            try {
                String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
                String[] parts = decoded.split(":", 2);

                if (parts.length != 2 || !parts[0].equals(adminUsername) || !parts[1].equals(adminPassword)) {
                    sendUnauthorized((HttpServletResponse) response);
                    return;
                }
            } catch (Exception e) {
                sendUnauthorized((HttpServletResponse) response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setHeader("WWW-Authenticate", "Basic realm=\"Admin Panel\"");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized. Please provide admin credentials.\"}");
    }
}
