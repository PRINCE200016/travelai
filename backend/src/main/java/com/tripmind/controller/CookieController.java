package com.tripmind.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cookies")
public class CookieController {

    @GetMapping("/init")
    public Map<String, String> initCookies(HttpServletResponse response) {
        // Create a tracking cookie
        Cookie cookie = new Cookie("TM_VISITOR_ID", "tripmind_" + System.currentTimeMillis());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Should be true for HTTPS (Hugging Face)
        cookie.setMaxAge(60 * 60 * 24 * 30); // 30 days
        
        // SameSite=None is required for cross-site cookies (Vercel to Hugging Face)
        // Spring Boot 3 / Jakarta EE handles this best via response header manually or filter
        // For simplicity, we'll set the header manually if needed, but standard Cookie object works in many cases.
        response.addCookie(cookie);

        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Cookies initialized");
        return result;
    }
}
