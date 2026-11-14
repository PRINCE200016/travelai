package com.tripmind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmind.model.PlanSearch;
import com.tripmind.planner.Catalog;
import com.tripmind.planner.Catalog.Destination;
import com.tripmind.planner.PlanRequest;
import com.tripmind.planner.PlanResult.Response;
import com.tripmind.planner.PlanResult.Trip;
import com.tripmind.planner.TripPlanner;
import com.tripmind.repository.PlanSearchRepository;
import com.tripmind.planner.Catalog.Origin;
import com.tripmind.planner.OriginResolver;
import com.tripmind.planner.ai.AiPlaceService;
import com.tripmind.service.WeatherService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PlanController {

    private static final Logger log = LoggerFactory.getLogger(PlanController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final TripPlanner planner;
    private final Catalog catalog;
    private final PlanSearchRepository searches;
    private final AiPlaceService aiPlaces;
    private final OriginResolver origins;
    private final WeatherService weather;
    private final ObjectMapper mapper;

    public PlanController(TripPlanner planner, Catalog catalog, PlanSearchRepository searches,
                          AiPlaceService aiPlaces, OriginResolver origins, WeatherService weather, ObjectMapper mapper) {
        this.planner = planner;
        this.catalog = catalog;
        this.searches = searches;
        this.aiPlaces = aiPlaces;
        this.origins = origins;
        this.weather = weather;
        this.mapper = mapper;
    }

    @PostMapping("/plan")
    public ResponseEntity<?> plan(@RequestBody PlanRequest req, HttpServletRequest http) {
        String error = req.normalise(LocalDate.now(IST));
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));

        Origin origin = origins.resolve(req.getOrigin()).orElse(null);
        if (origin == null) return ResponseEntity.ok(planner.originUnknown(req));

        // AI proposes places anywhere in India; they are verified, then costed by the same rules as everything else.
        // If the AI is off or returns too little, the built-in catalogue keeps the planner working.
        List<Destination> discovered = aiPlaces.discover(req, origin);
        boolean aiUsed = discovered.size() >= 3;
        if (aiUsed) req.usePool(discovered);
        Response res = planner.plan(req, origin);
        res.query().put("engine", aiUsed ? "ai" : "catalog");
        res.query().put("aiAvailable", aiPlaces.enabled());

        record(req, res, http);
        return ResponseEntity.ok(res);
    }

    private void record(PlanRequest req, Response res, HttpServletRequest http) {
        try {
            PlanSearch s = new PlanSearch();
            if (http.getAttribute("userId") instanceof Long uid) s.setUserId(uid);
            s.setQuery(mapper.writeValueAsString(res.query()));
            s.setStatus(res.status());
            Trip top = !res.recommendations().isEmpty() ? res.recommendations().get(0)
                    : res.stretch() != null && !res.stretch().isEmpty() ? res.stretch().get(0) : null;
            if (top != null) {
                s.setTopDestination(top.name());
                s.setTopDestinationId(top.id());
                s.setTotal(top.cost().total());
            }
            searches.save(s);
        } catch (Exception e) {
            log.warn("Could not record plan search: {}", e.getMessage());
        }
    }

    @GetMapping("/origins")
    public ResponseEntity<List<String>> origins() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic()).body(catalog.originNames());
    }

    @GetMapping("/destinations")
    public ResponseEntity<List<Map<String, Object>>> destinations() {
        List<Map<String, Object>> out = catalog.destinations().stream().map(this::summary).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(out);
    }

    @GetMapping("/destinations/{id}")
    public ResponseEntity<?> destination(@PathVariable String id) {
        return catalog.destination(id).<ResponseEntity<?>>map(d -> {
            Map<String, Object> m = summary(d);
            m.put("highlights", d.highlights());
            m.put("cautions", d.cautionsFor(null));
            m.put("highC", d.highC());
            m.put("lowC", d.lowC());
            m.put("railhead", d.railhead());
            m.put("airport", d.airport());
            return ResponseEntity.ok(m);
        }).orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Destination not found")));
    }

    /** Live forecast by coordinates when the trip starts within ~15 days. Empty list otherwise. */
    @GetMapping("/forecast")
    public ResponseEntity<?> forecastAt(@RequestParam double lat, @RequestParam double lon, @RequestParam String date,
                                        @RequestParam(defaultValue = "3") int days) {
        if (lat < 5 || lat > 38 || lon < 67 || lon > 98) return ResponseEntity.badRequest().body(Map.of("error", "Location must be in India"));
        LocalDate start;
        try { start = LocalDate.parse(date); } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date"));
        }
        return ResponseEntity.ok(Map.of("days", weather.forecast(lat, lon, start, Math.min(Math.max(days, 1), 7))));
    }

    /** Live forecast for a catalogue destination when the trip starts within ~15 days. Empty list otherwise. */
    @GetMapping("/destinations/{id}/forecast")
    public ResponseEntity<?> forecast(@PathVariable String id, @RequestParam String date,
                                      @RequestParam(defaultValue = "3") int days) {
        Destination d = catalog.destination(id).orElse(null);
        if (d == null) return ResponseEntity.status(404).body(Map.of("error", "Destination not found"));
        LocalDate start;
        try { start = LocalDate.parse(date); } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date"));
        }
        return ResponseEntity.ok(Map.of("days", weather.forecast(d.lat(), d.lon(), start, Math.min(Math.max(days, 1), 7))));
    }

    @GetMapping("/me/plans")
    public ResponseEntity<?> myPlans(HttpServletRequest http) {
        if (!(http.getAttribute("userId") instanceof Long uid)) {
            return ResponseEntity.status(401).body(Map.of("error", "Sign in to see your history"));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlanSearch s : searches.findByUserIdOrderByCreatedAtDesc(uid, PageRequest.of(0, 30))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("createdAt", s.getCreatedAt());
            m.put("status", s.getStatus());
            m.put("topDestination", s.getTopDestination());
            m.put("topDestinationId", s.getTopDestinationId());
            m.put("total", s.getTotal());
            try { m.put("query", mapper.readTree(s.getQuery())); } catch (Exception e) { m.put("query", null); }
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> summary(Destination d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id());
        m.put("name", d.name());
        m.put("state", d.state());
        m.put("image", d.image());
        m.put("imageAlt", d.imageAlt());
        m.put("summary", d.summary());
        m.put("tags", d.tags().entrySet().stream().filter(e -> e.getValue() >= 0.6)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).map(Map.Entry::getKey).toList());
        m.put("bestMonths", d.best().stream().sorted().toList());
        m.put("terrain", d.terrain());
        // Rough per-person daily spend at standard comfort, excluding getting there.
        int perDay = (int) Math.round((2900 / 2.0 + 1000 + 400) * d.costIndex() + d.activityPerDay());
        m.put("dailyCostFrom", (perDay / 100) * 100);
        return m;
    }
}
