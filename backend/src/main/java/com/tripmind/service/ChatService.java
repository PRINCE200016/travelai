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
                        ConstraintDecisionEngine constraintDecisionEngine) {
        this.weatherService = weatherService;
        this.crowdService = crowdService;
        this.chatHistoryRepo = chatHistoryRepo;
        this.constraintDecisionEngine = constraintDecisionEngine;
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
            return response;
        }

        if (engineResult.status() == ConstraintDecisionEngine.Status.FAIL) {
            ChatResponse response = new ChatResponse();
            response.setStatus("fail");
            response.setReason("constraints_too_low");

            Map<String, Object> req = new HashMap<>();
            req.put("minBudget", engineResult.minRequiredBudget());
            req.put("minDays", engineResult.recommendedDays());
            if (engineResult.reduceDaysToFitBudget() != null) {
                req.put("alternative", Map.of("reduceDaysToFitBudget", engineResult.reduceDaysToFitBudget()));
            }
            response.setRequired(req);
            response.setAlternatives(engineResult.alternatives());

            response.setExplanation("Flights and stay costs are high for this destination constraints. You can either increase your budget, or reduce trip duration.");
            response.setDestination("Your budget and duration are restrictive for this trip.");
            response.setRestrictive(true);
            
            // Maintain backwards compatibility values just in case
            response.setMinimumRequiredBudgetIncrease(Math.max(0, engineResult.minRequiredBudget() - budget));
            response.setMinimumRequiredDaysIncrease(Math.max(0, engineResult.recommendedDays() - duration));
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

        response.setJustification(softPassWarning + decision.reason());

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

    // Removed legacy suggestConstraintFix loop in favor of dynamic engineResult logic
}
