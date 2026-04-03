package com.tripmind.controller;

import com.tripmind.model.Lead;
import com.tripmind.repository.LeadRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@CrossOrigin(origins = "*")
public class LeadController {

    private final LeadRepository repository;

    public LeadController(LeadRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<Lead>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<Lead> create(@RequestBody Lead lead) {
        return ResponseEntity.ok(repository.save(lead));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCSV() {
        List<Lead> leads = repository.findAll();
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Budget,Destination Interest,Created At\n");
        
        for (Lead lead : leads) {
            csv.append(String.format("%d,\"%s\",\"%s\",\"%s\"\n",
                    lead.getId(),
                    lead.getBudget() != null ? lead.getBudget() : "",
                    lead.getDestinationInterest() != null ? lead.getDestinationInterest() : "",
                    lead.getCreatedAt() != null ? lead.getCreatedAt().toString() : ""
            ));
        }

        byte[] bytes = csv.toString().getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leads.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
