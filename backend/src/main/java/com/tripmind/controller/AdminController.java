package com.tripmind.controller;

import com.tripmind.model.ContactMessage;
import com.tripmind.model.ItineraryRequest;
import com.tripmind.model.Lead;
import com.tripmind.model.PaymentRequest;
import com.tripmind.model.PlanSearch;
import com.tripmind.model.TravelPackage;
import com.tripmind.model.User;
import com.tripmind.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything under /api/admin is protected by {@link com.tripmind.config.AdminAuthFilter}. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Set<String> REQUEST_STATUSES = Set.of("pending", "in_progress", "sent", "closed");
    private static final Set<String> CONTACT_STATUSES = Set.of("unread", "read", "replied");

    private final ItineraryRequestRepository itineraries;
    private final PackageRepository packages;
    private final LeadRepository leads;
    private final PaymentRequestRepository payments;
    private final ContactRepository contacts;
    private final UserRepository users;
    private final PlanSearchRepository plans;

    public AdminController(ItineraryRequestRepository itineraries, PackageRepository packages, LeadRepository leads,
                           PaymentRequestRepository payments, ContactRepository contacts, UserRepository users,
                           PlanSearchRepository plans) {
        this.itineraries = itineraries;
        this.packages = packages;
        this.leads = leads;
        this.payments = payments;
        this.contacts = contacts;
        this.users = users;
        this.plans = plans;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("users", users.count());
        s.put("plans", plans.count());
        s.put("itineraryRequests", itineraries.count());
        s.put("pendingRequests", itineraries.countByStatus("pending"));
        s.put("contactUnread", contacts.countByStatus("unread"));
        s.put("leads", leads.count());
        List<Map<String, Object>> top = new ArrayList<>();
        for (Object[] row : plans.topDestinations(PageRequest.of(0, 10))) {
            top.add(Map.of("destination", row[0], "count", row[1]));
        }
        s.put("topDestinations", top);
        return s;
    }

    // ---- Itinerary requests
    @GetMapping("/itinerary-requests")
    public List<ItineraryRequest> itineraryRequests() { return itineraries.findAllByOrderByCreatedAtDesc(); }

    @PutMapping("/itinerary-requests/{id}/status")
    public ResponseEntity<?> itineraryStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (!REQUEST_STATUSES.contains(status)) return ResponseEntity.badRequest().body(Validate.error("Unknown status"));
        return itineraries.findById(id).<ResponseEntity<?>>map(r -> {
            r.setStatus(status);
            return ResponseEntity.ok(itineraries.save(r));
        }).orElseGet(() -> ResponseEntity.status(404).body(Validate.error("Request not found")));
    }

    @DeleteMapping("/itinerary-requests/{id}")
    public ResponseEntity<?> deleteItinerary(@PathVariable Long id) {
        if (!itineraries.existsById(id)) return ResponseEntity.status(404).body(Validate.error("Request not found"));
        itineraries.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Contact messages
    @GetMapping("/contact")
    public List<ContactMessage> contactMessages() { return contacts.findAllByOrderByCreatedAtDesc(); }

    @PutMapping("/contact/{id}/status")
    public ResponseEntity<?> contactStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (!CONTACT_STATUSES.contains(status)) return ResponseEntity.badRequest().body(Validate.error("Unknown status"));
        return contacts.findById(id).<ResponseEntity<?>>map(m -> {
            m.setStatus(status);
            return ResponseEntity.ok(contacts.save(m));
        }).orElseGet(() -> ResponseEntity.status(404).body(Validate.error("Message not found")));
    }

    @DeleteMapping("/contact/{id}")
    public ResponseEntity<?> deleteContact(@PathVariable Long id) {
        if (!contacts.existsById(id)) return ResponseEntity.status(404).body(Validate.error("Message not found"));
        contacts.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Users, payments, planner searches
    @GetMapping("/users")
    public List<User> users() { return users.findAllByOrderByCreatedAtDesc(); }

    @GetMapping("/payments")
    public List<PaymentRequest> payments() { return payments.findAllByOrderByCreatedAtDesc(); }

    @GetMapping("/plans")
    public List<PlanSearch> plans() { return plans.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 500)); }

    // ---- Leads
    @GetMapping("/leads")
    public List<Lead> leads() { return leads.findAllByOrderByCreatedAtDesc(); }

    @GetMapping("/leads/export")
    public ResponseEntity<byte[]> exportLeads() {
        StringBuilder csv = new StringBuilder("id,name,phone,budget,destination_interest,created_at\n");
        for (Lead l : leads.findAllByOrderByCreatedAtDesc()) {
            csv.append(l.getId()).append(',')
                    .append(cell(l.getName())).append(',')
                    .append(cell(l.getPhone())).append(',')
                    .append(cell(l.getBudget())).append(',')
                    .append(cell(l.getDestinationInterest())).append(',')
                    .append(cell(l.getCreatedAt() == null ? "" : l.getCreatedAt().toString())).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tripmind-leads-" + LocalDate.now() + ".csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("﻿" + csv).getBytes(StandardCharsets.UTF_8));
    }

    /** Quote a CSV cell and neutralise spreadsheet formula injection. */
    static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---- Packages
    @GetMapping("/packages")
    public List<TravelPackage> allPackages() { return packages.findAll(); }

    @PostMapping("/packages")
    public ResponseEntity<?> createPackage(@RequestBody Map<String, Object> body) {
        TravelPackage p = new TravelPackage();
        String err = applyPackage(p, body);
        return err != null ? ResponseEntity.badRequest().body(Validate.error(err)) : ResponseEntity.ok(packages.save(p));
    }

    @PutMapping("/packages/{id}")
    public ResponseEntity<?> updatePackage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        TravelPackage p = packages.findById(id).orElse(null);
        if (p == null) return ResponseEntity.status(404).body(Validate.error("Package not found"));
        String err = applyPackage(p, body);
        return err != null ? ResponseEntity.badRequest().body(Validate.error(err)) : ResponseEntity.ok(packages.save(p));
    }

    @DeleteMapping("/packages/{id}")
    public ResponseEntity<?> deletePackage(@PathVariable Long id) {
        if (!packages.existsById(id)) return ResponseEntity.status(404).body(Validate.error("Package not found"));
        packages.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static String applyPackage(TravelPackage p, Map<String, Object> body) {
        String title = Validate.str(body, "title", 160);
        if (title == null) return "Title is required";
        String image = Validate.str(body, "imageUrl", 1000);
        if (image != null && !image.matches("^https?://.+")) return "Image URL must start with http:// or https://";
        Integer price = Validate.integer(body, "price", 0, 10_000_000);
        p.setTitle(title);
        p.setPrice(price == null ? null : price.doubleValue());
        p.setImageUrl(image);
        p.setDescription(Validate.str(body, "description", 4000));
        p.setFeatured(Boolean.TRUE.equals(body.get("featured")) || "true".equals(String.valueOf(body.get("featured"))));
        return null;
    }
}
