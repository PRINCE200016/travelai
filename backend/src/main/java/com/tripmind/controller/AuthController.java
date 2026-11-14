package com.tripmind.controller;

import com.tripmind.config.Tokens;
import com.tripmind.model.AuthSession;
import com.tripmind.model.User;
import com.tripmind.repository.AuthSessionRepository;
import com.tripmind.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int SESSION_DAYS = 30;
    // Used to keep login timing similar whether or not the email exists.
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("timing-equaliser");

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository users, AuthSessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        String name = Validate.str(body, "name", 80);
        String email = Validate.str(body, "email", 200);
        Object pw = body.get("password");
        String password = pw == null ? null : String.valueOf(pw);

        if (name == null) return bad("Please enter your name.");
        if (!Validate.email(email)) return bad("Please enter a valid email address.");
        if (password == null || password.length() < 8) return bad("Use a password of at least 8 characters.");
        if (password.length() > 72) return bad("Use a password of at most 72 characters.");
        email = email.toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Validate.error("An account with this email already exists. Try signing in."));
        }

        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword(encoder.encode(password));
        User saved = users.save(u);
        return ResponseEntity.ok(payload(saved, issue(saved.getId())));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String email = Validate.str(body, "email", 200);
        Object pw = body.get("password");
        String password = pw == null ? "" : String.valueOf(pw);
        Optional<User> user = email == null ? Optional.empty() : users.findByEmailIgnoreCase(email.trim());

        String hash = user.map(User::getPassword).orElse(DUMMY_HASH);
        boolean ok = hash != null && hash.startsWith("$2") && encoder.matches(password, hash);
        if (user.isEmpty() || !ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Validate.error("That email and password don't match an account."));
        }
        return ResponseEntity.ok(payload(user.get(), issue(user.get().getId())));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            sessions.deleteByToken(Tokens.hash(auth.substring(7).trim()));
        }
        return ResponseEntity.ok(Map.of("status", "signed_out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest http) {
        if (!(http.getAttribute("userId") instanceof Long uid)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Validate.error("Not signed in"));
        }
        return users.findById(uid).<ResponseEntity<?>>map(u -> ResponseEntity.ok(payload(u, null)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Validate.error("Not signed in")));
    }

    private String issue(Long userId) {
        String token = Tokens.newToken();
        AuthSession s = new AuthSession();
        s.setToken(Tokens.hash(token));
        s.setUserId(userId);
        s.setExpiresAt(LocalDateTime.now().plusDays(SESSION_DAYS));
        sessions.save(s);
        return token;
    }

    private static Map<String, Object> payload(User u, String token) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("createdAt", u.getCreatedAt());
        if (token != null) m.put("token", token);
        return m;
    }

    private static ResponseEntity<?> bad(String msg) {
        return ResponseEntity.badRequest().body(Validate.error(msg));
    }
}
