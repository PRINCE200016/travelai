package com.tripmind.controller;

import com.tripmind.model.User;
import com.tripmind.model.AuthSession;
import com.tripmind.repository.AuthSessionRepository;
import com.tripmind.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if(user.getEmail() == null || user.getEmail().trim().isEmpty() || user.getPassword() == null || user.getPassword().isEmpty()) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "Email and password are required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        if(userRepository.findByEmail(user.getEmail()).isPresent()) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "Email already registered");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
        
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);
        String token = createSessionToken(savedUser.getId());
        return ResponseEntity.ok(authPayload(savedUser, token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");

        Optional<User> userOptional = userRepository.findByEmail(email);
        
        if(userOptional.isPresent()) {
            User matchedUser = userOptional.get();
            boolean passwordMatched = passwordEncoder.matches(password, matchedUser.getPassword());

            // Backward-compatible path for legacy plaintext users.
            if (!passwordMatched && matchedUser.getPassword().equals(password)) {
                matchedUser.setPassword(passwordEncoder.encode(password));
                userRepository.save(matchedUser);
                passwordMatched = true;
            }

            if (!passwordMatched) {
                Map<String, String> err = new HashMap<>();
                err.put("error", "Invalid email or password");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
            }

            String token = createSessionToken(matchedUser.getId());
            return ResponseEntity.ok(authPayload(matchedUser, token));
        }

        Map<String, String> err = new HashMap<>();
        err.put("error", "Invalid email or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authSessionRepository.deleteByToken(authHeader.substring(7));
        }
        return ResponseEntity.ok(Map.of("status", "logged_out"));
    }

    private String createSessionToken(Long userId) {
        AuthSession session = new AuthSession();
        session.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        return authSessionRepository.save(session).getToken();
    }

    private Map<String, Object> authPayload(User user, String token) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", user.getId());
        payload.put("name", user.getName());
        payload.put("email", user.getEmail());
        payload.put("createdAt", user.getCreatedAt());
        payload.put("token", token);
        return payload;
    }
}
