package com.tripmind.controller;

import com.tripmind.model.ContactMessage;
import com.tripmind.repository.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public contact form. Reading and managing messages lives in AdminController. */
@RestController
@RequestMapping("/api")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);
    private final ContactRepository contactRepo;

    public ContactController(ContactRepository contactRepo) {
        this.contactRepo = contactRepo;
    }

    @PostMapping("/contact")
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body) {
        String name = Validate.str(body, "name", 120);
        String email = Validate.str(body, "email", 200);
        String phone = Validate.str(body, "phone", 20);
        String message = Validate.str(body, "message", 4000);
        if (name == null) return ResponseEntity.badRequest().body(Validate.error("Please enter your name."));
        if (!Validate.email(email)) return ResponseEntity.badRequest().body(Validate.error("Please enter a valid email address."));
        if (!Validate.phone(phone)) return ResponseEntity.badRequest().body(Validate.error("Please enter a valid phone number."));
        if (message == null || message.length() < 5) return ResponseEntity.badRequest().body(Validate.error("Please write a short message."));

        ContactMessage m = new ContactMessage();
        m.setName(name);
        m.setEmail(email);
        m.setPhone(phone);
        m.setMessage(message);
        m.setStatus("unread");
        contactRepo.save(m);
        log.info("Contact message received (id={})", m.getId());
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
