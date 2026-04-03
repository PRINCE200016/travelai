package com.tripmind.controller;

import com.tripmind.model.ContactMessage;
import com.tripmind.repository.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);
    private final ContactRepository contactRepo;

    public ContactController(ContactRepository contactRepo) {
        this.contactRepo = contactRepo;
    }

    // Public endpoint for submitting contact forms
    @PostMapping("/contact")
    public ResponseEntity<Map<String, String>> submitContact(@RequestBody ContactMessage message) {
        if (message.getName() == null || message.getEmail() == null || message.getMessage() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name, email, and message are required"));
        }
        
        contactRepo.save(message);
        log.info("New contact message received from {}", message.getEmail());
        
        // In a real app, send an email to arjunrajawat28@gmail.com here
        
        return ResponseEntity.ok(Map.of("status", "Message received successfully"));
    }

    // Secured endpoint for admin panel
    @GetMapping("/admin/contact")
    public ResponseEntity<List<ContactMessage>> getAllMessages() {
        return ResponseEntity.ok(contactRepo.findAllByOrderByCreatedAtDesc());
    }

    @PutMapping("/admin/contact/{id}/status")
    public ResponseEntity<Map<String, String>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ContactMessage msg = contactRepo.findById(id).orElse(null);
        if (msg == null) return ResponseEntity.notFound().build();

        msg.setStatus(body.getOrDefault("status", "read"));
        contactRepo.save(msg);
        return ResponseEntity.ok(Map.of("status", "Updated"));
    }

    @DeleteMapping("/admin/contact/{id}")
    public ResponseEntity<Map<String, String>> deleteMessage(@PathVariable Long id) {
        if (contactRepo.existsById(id)) {
            contactRepo.deleteById(id);
            return ResponseEntity.ok(Map.of("status", "Deleted"));
        }
        return ResponseEntity.notFound().build();
    }
}
