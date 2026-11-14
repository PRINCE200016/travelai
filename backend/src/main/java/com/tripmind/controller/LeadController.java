package com.tripmind.controller;

import com.tripmind.model.Lead;
import com.tripmind.repository.LeadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public: someone asks to be contacted about a destination. Listing/export lives in AdminController. */
@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadRepository repository;

    public LeadController(LeadRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String name = Validate.str(body, "name", 120);
        String phone = Validate.str(body, "phone", 20);
        if (name == null || phone == null || !Validate.phone(phone)) {
            return ResponseEntity.badRequest().body(Validate.error("Name and a valid phone number are required."));
        }
        Lead lead = new Lead();
        lead.setName(name);
        lead.setPhone(phone);
        lead.setBudget(Validate.str(body, "budget", 40));
        lead.setDestinationInterest(Validate.str(body, "destinationInterest", 160));
        repository.save(lead);
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
