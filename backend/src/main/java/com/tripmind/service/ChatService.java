package com.tripmind.service;

import com.tripmind.dto.ChatRequest;
import com.tripmind.dto.ChatResponse;
import com.tripmind.model.ChatHistory;
import com.tripmind.repository.ChatHistoryRepository;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final Gson gson = new Gson();

    private final WeatherService weatherService;
    private final CrowdService crowdService;
    private final ChatHistoryRepository chatHistoryRepo;
    private final ConstraintDecisionEngine constraintDecisionEngine;
    private final DistanceService distanceService;

    private static final Map<String, String> DESTINATION_IMAGES = Map.ofEntries(
            Map.entry("manali", "https://images.unsplash.com/photo-1626621341517-bbf3d9990a23?w=1200&h=600&fit=crop"),
            Map.entry("goa", "https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?w=1200&h=600&fit=crop"),
            Map.entry("munnar", "https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=1200&h=600&fit=crop"),
            Map.entry("varanasi", "https://images.unsplash.com/photo-1561361513-2d000a50f0dc?w=1200&h=600&fit=crop"),
            Map.entry("kashmir", "https://images.unsplash.com/photo-1595815771614-ade9d652a65d?w=1200&h=600&fit=crop"),
            Map.entry("jaipur", "https://images.unsplash.com/photo-1477587458883-47145ed94245?w=1200&h=600&fit=crop"),
            Map.entry("rishikesh", "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?w=1200&h=600&fit=crop")
    );

    public ChatService(WeatherService weatherService,
                        CrowdService crowdService,
                        ChatHistoryRepository chatHistoryRepo,
                        ConstraintDecisionEngine constraintDecisionEngine,
                        DistanceService distanceService) {
        this.weatherService = weatherService;
        this.crowdService = crowdService;
        this.chatHistoryRepo = chatHistoryRepo;
        this.constraintDecisionEngine = constraintDecisionEngine;
        this.distanceService = distanceService;
    }

    public ChatResponse processChat(ChatRequest request) {
        // STEP 1: Read & validate user input (minimal sanitation only).
        request.validate();

        log.info("Processing chat: budget={}, mood={}, stress={}, profile={}",
                request.getBudget(), request.getMood(), request.getStressLevel(), request.getPsychProfile());

        int duration = request.getDuration() != null ? request.getDuration() : 3;
        int travelers = request.getTravelersCount() != null ? request.getTravelersCount() : 2;
        int budget = request.getBudget() != null ? request.getBudget() : 10000;
        String originCity = request.getOriginCity() != null ? request.getOriginCity() : "Unknown";

        // HARD CONSTRAINT PIPELINE
        ConstraintDecisionEngine.EngineResult engineResult = constraintDecisionEngine.decide(
                originCity,
                budget,
                duration,
                request.getTravelType(),
                travelers
        );

        if (engineResult.status() == ConstraintDecisionEngine.Status.UNREALISTIC) {
            ChatResponse response = new ChatResponse();
            response.setStatus("fail");
            response.setReason("budget_unrealistic");
            response.setExplanation("The minimum required budget for any valid destination is over ₹300,000. Please try a closer or more affordable destination.");
            response.setDestination("Trip parameters are unrealistic.");
            response.setRestrictive(true);
            response.setBudgetEscalationAllowed(Boolean.FALSE);
            return response;
        }

        if (engineResult.status() == ConstraintDecisionEngine.Status.FAIL) {
            ChatResponse response = buildConstraintFailureResponse(
                    request,
                    engineResult,
                    budget,
                    duration
            );
            return response;
        }

        ConstraintDecisionEngine.Decision decision = engineResult.decision();
        
        String softPassWarning = "";
        if (engineResult.status() == ConstraintDecisionEngine.Status.SOFT_PASS) {
            softPassWarning = "⚠️ This plan slightly exceeds your budget but is manageable. ";
        }

        String destination = decision.destinationDisplay();
        String cityName = destination.split(",")[0].trim();

        Map<String, Object> weatherData = weatherService.getWeather(cityName);
        Map<String, String> crowdData = crowdService.getCrowdLevel(cityName);

        ChatResponse response = new ChatResponse();
        response.setDestination(destination);
        response.setDescription(softPassWarning); // constraint engine: do not elaborate before validation 

        String destKey = decision.destinationKey().toLowerCase();
        response.setImageUrl(DESTINATION_IMAGES.getOrDefault(destKey,
                "https://images.unsplash.com/photo-1488646953014-85cb44e25828?w=1200&h=600&fit=crop"));

        response.setWeather(weatherData.get("desc") + " — " + weatherData.get("temp") + "°C");
        response.setWeatherData(weatherData);

        response.setCrowd(crowdData.get("level"));
        response.setCrowdReason(crowdData.get("reason"));

        // Put strict breakdown where frontend already expects it.
        Map<String, Object> budgetData = new HashMap<>(decision.costBreakdown());
        budgetData.put("originCity", request.getOriginCity());
        response.setBudgetEstimate(budgetData);

        String justification = softPassWarning + decision.reason();
        if (!distanceService.isOriginMapped(originCity)) {
            justification = "(Approximate routing from your area — add a major hub if you want tighter mileage.) " + justification;
        }
        response.setJustification(justification);

        // Minimal safe activities list (low-budget friendly when applicable)
        if (budget <= 1000) {
            response.setActivities(List.of("Temple visit", "River/ghat walk", "Local street food", "Photography (free)"));
        } else if (budget <= 2000) {
            response.setActivities(List.of("Local sightseeing", "Temple visit", "Market walk", "Photography"));
        } else {
            response.setActivities(List.of("Local sightseeing", "Food crawl", "Cultural spots", "Photography"));
        }

        // Provide strict cost breakdown as well (frontend chat UI prints it if present).
        response.setCostBreakdown(new HashMap<>(decision.costBreakdown()));
        response.setRestrictive(false);

        // 6. Save chat history
        try {
            ChatHistory history = new ChatHistory();
            history.setSessionId("api_" + System.currentTimeMillis());
            if(request.getUserId() != null) {
                history.setUserId(request.getUserId());
            }
            history.setUserInput(gson.toJson(request));
            history.setAiResponse(gson.toJson(response));
            history.setDestination(destination);
            chatHistoryRepo.save(history);
        } catch (Exception e) {
            log.warn("Failed to save chat history: {}", e.getMessage());
        }

        log.info("Chat response ready: {}", destination);
        return response;
    }

    private ChatResponse buildConstraintFailureResponse(ChatRequest request,
                                                        ConstraintDecisionEngine.EngineResult engineResult,
                                                        int budget,
                                                        int duration) {
        ChatResponse response = new ChatResponse();
        response.setStatus("fail");
        response.setRestrictive(true);
        response.setAlternatives(engineResult.alternatives());

        int attempts = request.getConstraintEscalationAttempts() != null ? request.getConstraintEscalationAttempts() : 0;
        boolean escalationExhausted = attempts >= 2;

        String engineReason = engineResult.constraintReason();
        boolean tooExpensive = ConstraintDecisionEngine.REASON_DESTINATION_TOO_EXPENSIVE.equals(engineReason);
        boolean noCandidates = ConstraintDecisionEngine.REASON_NO_CANDIDATES.equals(engineReason);
        boolean suggestMildAdjustment = ConstraintDecisionEngine.REASON_SUGGEST_ADJUSTMENT.equals(engineReason);

        Integer cheapest = engineResult.informationalCheapestCost();
        if (cheapest != null && (tooExpensive || suggestMildAdjustment)) {
            response.setApproximateCheapestTotal(cheapest);
        }

        boolean allowBudgetEscalation =
                suggestMildAdjustment
                        && engineResult.minRequiredBudget() != null
                        && !escalationExhausted;
        response.setBudgetEscalationAllowed(Boolean.valueOf(allowBudgetEscalation));

        String apiReason;
        if (escalationExhausted && (suggestMildAdjustment || tooExpensive)) {
            apiReason = "escalation_exhausted";
        } else if (tooExpensive) {
            apiReason = "destination_too_expensive";
        } else if (noCandidates) {
            apiReason = "no_candidates_in_range";
        } else {
            apiReason = "constraints_too_low";
        }
        response.setReason(apiReason);

        Map<String, Object> req = new HashMap<>();
        if (allowBudgetEscalation) {
            req.put("minBudget", engineResult.minRequiredBudget());
            req.put("minDays", engineResult.recommendedDays());
        }
        if (engineResult.reduceDaysToFitBudget() != null) {
            req.put("alternative", Map.of("reduceDaysToFitBudget", engineResult.reduceDaysToFitBudget()));
        }
        if (noCandidates && engineResult.recommendedDays() != null) {
            req.put("suggestedNextDays", engineResult.recommendedDays());
        }
        if (req.isEmpty()) {
            response.setRequired(null);
        } else {
            response.setRequired(req);
        }

        ConstraintDecisionEngine.Decision cheapestFail = engineResult.cheapestFailingDecision();
        String samplePlace = cheapestFail != null ? shortCity(cheapestFail.destinationDisplay()) : "similar trips";

        if ("escalation_exhausted".equals(apiReason)) {
            response.setExplanation("We already tried stretching your plan twice. Your original budget and duration stay as you set them. "
                    + "Here are the most affordable options we still see -- pick one you're willing to plan for, or adjust budget/days yourself when you're ready.");
            response.setDestination("Your budget and duration are too restrictive for automatic changes.");
        } else if (noCandidates) {
            response.setExplanation("No destinations fit the one-day distance window from your city. Try a longer trip duration so you can reach places a bit farther away, "
                    + "or type a more specific nearby place you have in mind.");
            response.setDestination("Your budget and duration are too restrictive for trips in the current distance window.");
        } else if (tooExpensive) {
            response.setExplanation(String.format(
                    Locale.ROOT,
                    "Even the most affordable reachable options from your city (example: %s) are still much higher than your ₹%,d budget for this duration — "
                            + "so forcing one place would mean overruling what you asked for. Compare the alternatives below, or only raise budget/days if you choose to.",
                    samplePlace,
                    budget
            ));
            response.setDestination("This setup is expensive for realistic travel from your origin.");
        } else {
            response.setExplanation(String.format(
                    Locale.ROOT,
                    "Travel and stay for %s are tight but not wildly above your ₹%,d budget. "
                            + "You can nudge budget or shorten the trip if you want this style of destination — otherwise browse the cheaper alternatives.",
                    samplePlace,
                    budget
            ));
            response.setDestination("Your budget and duration are too restrictive for this trip.");
        }

        if (allowBudgetEscalation && engineResult.minRequiredBudget() != null) {
            response.setMinimumRequiredBudgetIncrease(Math.max(0, engineResult.minRequiredBudget() - budget));
        }
        if (allowBudgetEscalation && engineResult.recommendedDays() != null) {
            response.setMinimumRequiredDaysIncrease(Math.max(0, engineResult.recommendedDays() - duration));
        }

        if (!distanceService.isOriginMapped(request.getOriginCity())) {
            response.setExplanation(
                    "Routing from your city uses typical India travel distances until it is on our coordinate map (check spelling). "
                    + response.getExplanation());
        }

        return response;
    }

    private static String shortCity(String display) {
        if (display == null) return "nearby spots";
        int c = display.indexOf(',');
        return c > 0 ? display.substring(0, c).trim() : display.trim();
    }

    // Removed legacy suggestConstraintFix loop in favor of dynamic engineResult logic
}
