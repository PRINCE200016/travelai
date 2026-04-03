package com.tripmind.controller;

import com.tripmind.model.User;
import com.tripmind.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/admin/users")
    public ResponseEntity<List<User>> getUsers() {
        List<User> users = userRepository.findAllByOrderByCreatedAtDesc();
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id, HttpServletRequest httpRequest) {
        Object attrUserId = httpRequest.getAttribute("userId");
        if (!(attrUserId instanceof Long) || !attrUserId.equals(id)) {
            return ResponseEntity.status(403).build();
        }

        return userRepository.findById(id)
                .map(u -> {
                    u.setPassword(null);
                    return ResponseEntity.ok(u);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                @RequestParam(value = "userId", required = false) Long userId,
                                HttpServletRequest httpRequest) {
        if (userId == null) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "userId is required");
            return ResponseEntity.badRequest().body(err);
        }
        return getUser(userId, httpRequest);
    }
}
