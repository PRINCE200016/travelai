package com.tripmind.controller;

import com.tripmind.model.ItineraryRequest;
import com.tripmind.repository.ItineraryRequestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ItineraryController {

    private final ItineraryRequestRepository repository;

    public ItineraryController(ItineraryRequestRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/itinerary-request")
    public ResponseEntity<ItineraryRequest> createRequest(@RequestBody ItineraryRequest request) {
        request.setStatus("pending");
        ItineraryRequest saved = repository.save(request);

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/itinerary-requests")
    public ResponseEntity<List<ItineraryRequest>> getAllRequests() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/itinerary-request/{id}")
    public ResponseEntity<ItineraryRequest> getRequest(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/itinerary-request/{id}/status")
    public ResponseEntity<ItineraryRequest> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repository.findById(id)
                .map(req -> {
                    req.setStatus(body.getOrDefault("status", "pending"));
                    return ResponseEntity.ok(repository.save(req));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/itinerary-request/{id}")
    public ResponseEntity<Void> deleteRequest(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
