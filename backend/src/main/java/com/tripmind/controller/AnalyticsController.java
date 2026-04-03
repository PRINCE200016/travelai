package com.tripmind.controller;

import com.tripmind.model.UserSession;
import com.tripmind.repository.ChatHistoryRepository;
import com.tripmind.repository.LeadRepository;
import com.tripmind.repository.PaymentRequestRepository;
import com.tripmind.repository.UserSessionRepository;
import com.tripmind.repository.ItineraryRequestRepository;
import com.tripmind.repository.PackageRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final ChatHistoryRepository chatHistoryRepo;
    private final UserSessionRepository sessionRepo;
    private final LeadRepository leadRepo;
    private final ItineraryRequestRepository itineraryRepo;
    private final PackageRepository packageRepo;
    private final PaymentRequestRepository paymentRequestRepository;

    public AnalyticsController(ChatHistoryRepository chatHistoryRepo,
                                UserSessionRepository sessionRepo,
                                LeadRepository leadRepo,
                                ItineraryRequestRepository itineraryRepo,
                                PackageRepository packageRepo,
                                PaymentRequestRepository paymentRequestRepository) {
        this.chatHistoryRepo = chatHistoryRepo;
        this.sessionRepo = sessionRepo;
        this.leadRepo = leadRepo;
        this.itineraryRepo = itineraryRepo;
        this.packageRepo = packageRepo;
        this.paymentRequestRepository = paymentRequestRepository;
    }

    @GetMapping("/admin/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();

        // Counts
        long totalSessions = sessionRepo.count();
        long totalChats = chatHistoryRepo.count();
        long totalLeads = leadRepo.count();
        long totalRequests = itineraryRepo.count();
        long totalPayments = paymentRequestRepository.count();

        analytics.put("totalSessions", totalSessions);
        analytics.put("totalChats", totalChats);
        analytics.put("totalLeads", totalLeads);
        analytics.put("totalRequests", totalRequests);
        analytics.put("totalPayments", totalPayments);
        analytics.put("totalPackages", packageRepo.count());

        int revenue = paymentRequestRepository.findAll().stream()
                .filter(p -> "paid".equalsIgnoreCase(p.getPaymentStatus()))
                .mapToInt(p -> p.getAmount() != null ? p.getAmount() : 0)
                .sum();
        analytics.put("estimatedRevenue", revenue);

        // Conversion rate: leads / sessions
        double conversionRate = totalSessions > 0 ? (double) totalLeads / totalSessions * 100 : 0;
        analytics.put("conversionRate", Math.round(conversionRate * 10) / 10.0);

        // Most searched destinations
        List<Object[]> topDest = chatHistoryRepo.findTopDestinations();
        List<Map<String, Object>> destinations = new ArrayList<>();
        for (int i = 0; i < Math.min(10, topDest.size()); i++) {
            Map<String, Object> d = new HashMap<>();
            d.put("destination", topDest.get(i)[0]);
            d.put("count", topDest.get(i)[1]);
            destinations.add(d);
        }
        analytics.put("topDestinations", destinations);

        return ResponseEntity.ok(analytics);
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, String>> trackSession(@RequestBody Map<String, String> body,
                                                             HttpServletRequest request) {
        UserSession session = new UserSession();
        session.setSessionId(body.getOrDefault("sessionId", "unknown"));
        session.setDevice(body.getOrDefault("device", "unknown"));

        // Get IP
        String xff = request.getHeader("X-Forwarded-For");
        session.setIpAddress(xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr());

        sessionRepo.save(session);
        return ResponseEntity.ok(Map.of("status", "tracked"));
    }
}
