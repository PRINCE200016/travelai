package com.tripmind.controller;

import com.tripmind.model.ItineraryRequest;
import com.tripmind.repository.ItineraryRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Public: a traveller asks for expert planning. Listing and status changes live in AdminController. */
@RestController
@RequestMapping("/api")
public class ItineraryController {

    private static final Logger log = LoggerFactory.getLogger(ItineraryController.class);
    private final ItineraryRequestRepository repository;

    public ItineraryController(ItineraryRequestRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/itinerary-request")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String name = Validate.str(body, "name", 120);
        String email = Validate.str(body, "email", 200);
        String phone = Validate.str(body, "phone", 20);
        String destination = Validate.str(body, "destination", 160);
        if (name == null) return ResponseEntity.badRequest().body(Validate.error("Please enter your name."));
        if (!Validate.email(email)) return ResponseEntity.badRequest().body(Validate.error("Please enter a valid email address."));
        if (!Validate.phone(phone)) return ResponseEntity.badRequest().body(Validate.error("Please enter a valid phone number."));
        if (destination == null) return ResponseEntity.badRequest().body(Validate.error("Tell us where you'd like to go."));

        String start = Validate.str(body, "startDate", 10);
        if (start == null) start = Validate.str(body, "travelStartDate", 10);
        if (start != null) {
            try {
                if (LocalDate.parse(start).isBefore(LocalDate.now().minusDays(1))) {
                    return ResponseEntity.badRequest().body(Validate.error("The travel date is in the past."));
                }
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Validate.error("Use a valid travel date."));
            }
        }

        ItineraryRequest r = new ItineraryRequest();
        r.setName(name);
        r.setEmail(email);
        r.setPhone(phone);
        r.setDestination(destination);
        r.setTravelStartDate(start);
        r.setDays(Validate.integer(body, "days", 1, 60));
        Integer travellers = Validate.integer(body, "travellers", 1, 50);
        r.setTravelers(travellers != null ? travellers : Validate.integer(body, "travelers", 1, 50));
        r.setBudget(Validate.integer(body, "budget", 0, 10_000_000));
        Object prefs = body.get("preferences");
        if (prefs instanceof List<?> list) prefs = String.join(", ", list.stream().map(String::valueOf).toList());
        r.setPreferences(prefs == null ? null : String.valueOf(prefs).substring(0, Math.min(300, String.valueOf(prefs).length())));
        String notes = Validate.str(body, "notes", 2000);
        r.setSpecialRequest(notes != null ? notes : Validate.str(body, "specialRequest", 2000));
        r.setStatus("pending");
        ItineraryRequest saved = repository.save(r);
        log.info("Expert planning request {} for {}", saved.getId(), destination);
        return ResponseEntity.ok(Map.of("id", saved.getId(), "status", "pending"));
    }
}
