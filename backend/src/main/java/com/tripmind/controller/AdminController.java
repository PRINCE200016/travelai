package com.tripmind.controller;

import com.tripmind.repository.ItineraryRequestRepository;
import com.tripmind.repository.LeadRepository;
import com.tripmind.repository.PaymentRequestRepository;
import com.tripmind.repository.PackageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final ItineraryRequestRepository itineraryRepo;
    private final PackageRepository packageRepo;
    private final LeadRepository leadRepo;
    private final PaymentRequestRepository paymentRepo;

    public AdminController(ItineraryRequestRepository itineraryRepo,
                           PackageRepository packageRepo,
                           LeadRepository leadRepo,
                           PaymentRequestRepository paymentRepo) {
        this.itineraryRepo = itineraryRepo;
        this.packageRepo = packageRepo;
        this.leadRepo = leadRepo;
        this.paymentRepo = paymentRepo;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", itineraryRepo.count());
        stats.put("totalPackages", packageRepo.count());
        stats.put("totalLeads", leadRepo.count());
        stats.put("totalPayments", paymentRepo.count());
        int paidRevenue = paymentRepo.findAll().stream()
                .filter(p -> "paid".equalsIgnoreCase(p.getPaymentStatus()))
                .mapToInt(p -> p.getAmount() != null ? p.getAmount() : 0)
                .sum();
        stats.put("revenue", paidRevenue);
        return ResponseEntity.ok(stats);
    }
}
