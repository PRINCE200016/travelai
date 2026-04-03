package com.tripmind.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private final WebClient webClient;
    private final Gson gson = new Gson();

    private static final int MAX_RETRIES = 2;
    private static final List<String> ITINERARY_PATTERNS = List.of(
            "Day 1", "Day 2", "Day 3", "day 1", "day 2",
            "itinerary", "Itinerary", "ITINERARY",
            "Morning:", "Afternoon:", "Evening:",
            "09:00", "10:00", "11:00", "12:00"
    );

    @Value("${gemini.api.key}")
    private String apiKey;

    public GeminiService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public Map<String, Object> getDestinationSuggestion(int budget, int duration, String travelType,
                                                          String mood, String weatherPref, String crowdTolerance,
                                                          String stressLevel, String psychProfile, String travelDate, 
                                                          String originCity, List<String> validDestinations) {
        if ("demo".equals(apiKey)) {
            log.info("Using demo mode (no Gemini API key configured)");
            return getFallbackSuggestion(budget, mood, psychProfile);
        }

        // Retry logic
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String prompt = buildPrompt(budget, duration, travelType, mood, weatherPref, crowdTolerance, stressLevel, psychProfile, travelDate, originCity, validDestinations);

                JsonObject requestBody = new JsonObject();
                JsonArray contents = new JsonArray();
                JsonObject content = new JsonObject();
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", prompt);
                parts.add(part);
                content.add("parts", parts);
                contents.add(content);
                requestBody.add("contents", contents);

                String response = webClient.post()
                        .uri("/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(gson.toJson(requestBody))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                Map<String, Object> result = parseGeminiResponse(response);

                // VALIDATION: Check for itinerary-like content
                if (containsItinerary(result)) {
                    log.warn("AI returned itinerary-like content on attempt {}. Retrying...", attempt + 1);
                    if (attempt < MAX_RETRIES) continue;
                    log.error("AI keeps returning itinerary after {} attempts. Trimming response.", MAX_RETRIES + 1);
                    result = trimItineraryContent(result);
                }

                return result;
            } catch (Exception e) {
                log.error("Gemini API error on attempt {}: {}", attempt + 1, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    return getFallbackSuggestion(budget, mood, psychProfile);
                }
            }
        }

        return getFallbackSuggestion(budget, mood, psychProfile);
    }

    /**
     * Validates that the response does NOT contain itinerary-like content.
     */
    private boolean containsItinerary(Map<String, Object> result) {
        String fullText = result.toString();
        for (String pattern : ITINERARY_PATTERNS) {
            if (fullText.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips itinerary-like content from response, keeping only valid fields.
     */
    private Map<String, Object> trimItineraryContent(Map<String, Object> result) {
        // Keep only the required fields
        Map<String, Object> cleaned = new HashMap<>();
        cleaned.put("destination", result.getOrDefault("destination", "Unknown"));
        cleaned.put("description", result.getOrDefault("description", "A wonderful travel destination."));

        // Clean activities — remove any that look like itinerary items
        Object acts = result.get("activities");
        if (acts instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> activities = (List<String>) acts;
            List<String> cleanedActs = new ArrayList<>();
            for (String act : activities) {
                boolean isItinerary = false;
                for (String pattern : ITINERARY_PATTERNS) {
                    if (act.contains(pattern)) { isItinerary = true; break; }
                }
                if (!isItinerary && cleanedActs.size() < 5) {
                    cleanedActs.add(act);
                }
            }
            cleaned.put("activities", cleanedActs.isEmpty() ?
                    List.of("🏔️ Sightseeing", "📸 Photography", "🍽️ Local Cuisine") : cleanedActs);
        }

        // Clean justification — remove itinerary content
        String justification = (String) result.getOrDefault("justification", "");
        for (String pattern : ITINERARY_PATTERNS) {
            int idx = justification.indexOf(pattern);
            if (idx > 0) {
                justification = justification.substring(0, idx).trim();
            }
        }
        cleaned.put("justification", justification.isEmpty() ?
                "Based on your preferences, this is the best destination match." : justification);

        return cleaned;
    }

    private String buildPrompt(int budget, int duration, String travelType,
                                String mood, String weatherPref, String crowdTolerance,
                                String stressLevel, String psychProfile, String travelDate, String originCity, List<String> validDestinations) {
        String psychContext = "";
        if (psychProfile != null && !psychProfile.isEmpty()) {
            psychContext = switch (psychProfile) {
                case "burnout_needs_isolation" -> "\nPSYCHOLOGICAL NOTE: User is highly stressed and burned out. Prioritize isolated, peaceful, nature-immersive destinations with minimal crowds. Mention stress-relief benefits in justification.";
                case "deeply_stressed_spiritual" -> "\nPSYCHOLOGICAL NOTE: User is deeply stressed seeking spiritual healing. Prioritize calm spiritual destinations with meditation opportunities. Mention mental healing benefits.";
                case "stressed_seeking_thrill" -> "\nPSYCHOLOGICAL NOTE: User is stressed but seeks adrenaline as an outlet. Suggest adventure destinations where physical activity helps release stress. Mention endorphin and dopamine benefits.";
                case "moderate_stress_needs_calm" -> "\nPSYCHOLOGICAL NOTE: User has moderate stress and needs calming scenery. Balance peaceful activities with mild exploration. Mention relaxation benefits.";
                case "moderate_stress_needs_change" -> "\nPSYCHOLOGICAL NOTE: User needs a change of environment to reset. Suggest a destination that contrasts with typical urban life.";
                case "social_energetic" -> "\nPSYCHOLOGICAL NOTE: User is socially driven and energetic. Suggest vibrant, social destinations with nightlife and group activities.";
                default -> "";
            };
        }

        return """
                You are an expert Indian travel planner AI. Based on the user's preferences, pick ONE Best destination in India.
                
                User Preferences:
                - Origin City: %s
                - Budget: ₹%d
                - Duration: %d days
                - Travel Type: %s
                - Mood: %s
                - Travel Date: %s
                - Stress Level: %s
                %s
                
                CRITICAL RULES:
                1. MATHEMATICAL CONSTRAINT LIST (STRICT): You MUST ONLY choose your destination from this exact list of mathematically valid cities: [%s].
                   - If this list is empty, you MUST return "destination": "None".
                   - Do NOT invent or recommend any city that is not exactly in this list.
                2. LOCATION-AWARE FILTERING: From the valid list, pick the best match for the user's mood.
                3. STRICT COST VALIDATION: Total trip cost MUST NOT exceed the user's budget. Calculate realistically:
                   - For a 1-day trip, assume ₹0 for hotel/stay.
                   - Distribute budget rationally across travel, food, and activities.
                4. DYNAMIC PRICING: If travel date is peak season, mention prices are higher.
                5. PROVIDE OPTIONS: Provide Option 1 (strictly safe within budget) and Option 2 (slightly upgraded).
                6. Do NOT generate day-by-day itineraries.
                
                Respond ONLY in this JSON format:
                {
                  "destination": "City Name, State",
                  "description": "2-3 sentences about destination. Mention peak/off-season implications based on travel date.",
                  "activities": ["Activity 1", "Activity 2", "Activity 3", "Activity 4", "Activity 5"],
                  "justification": "Why this matches mood, weather, budget, and distance.",
                  "option1": { "type": "Budget Safe", "details": "Description", "totalCost": 9000 },
                  "option2": { "type": "Slightly Upgraded", "details": "Description", "totalCost": 12000 },
                  "costBreakdown": { "travel": 3000, "hotel": 2000, "food": 2000, "localTransport": 1000, "activities": 1000 }
                }
                """.formatted(originCity != null ? originCity : "Unknown", budget, duration, travelType, mood, 
                travelDate != null ? travelDate : "Unknown", stressLevel != null ? stressLevel : "normal", psychContext, String.join(", ", validDestinations));
    }

    private Map<String, Object> parseGeminiResponse(String response) {
        try {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            String text = json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            text = text.replace("```json", "").replace("```", "").trim();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = gson.fromJson(text, Map.class);
            return result;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return getFallbackSuggestion(10000, "adventure", null);
        }
    }

    private Map<String, Object> getFallbackSuggestion(int budget, String mood, String psychProfile) {
        Map<String, Object> result = new HashMap<>();

        if (mood == null) mood = "adventure";
        mood = mood.toLowerCase();

        // Psychological override
        if ("burnout_needs_isolation".equals(psychProfile)) {
            mood = "relax";
        } else if ("deeply_stressed_spiritual".equals(psychProfile)) {
            mood = "spirit";
        }

        String stressNote = "";
        if (psychProfile != null && psychProfile.contains("stress")) {
            stressNote = " The serene environment will help reduce your stress levels and promote mental well-being.";
        }

        if (mood.contains("relax") || mood.contains("peace")) {
            result.put("destination", "Munnar, Kerala");
            result.put("description", "Lush green tea plantations, misty mountains, and serene landscapes make Munnar a paradise for peace seekers.");
            result.put("activities", Arrays.asList("🍵 Tea Plantation Tour", "🌄 Sunrise at Top Station", "🚣 Boating at Mattupetty Dam", "📸 Eravikulam National Park", "🌊 Attukal Waterfalls"));
            result.put("justification", String.format("We recommend Munnar because it perfectly matches your budget of ₹%d. The weather is cool and pleasant at 18°C, crowd levels are low making it ideal for relaxation, and it offers plenty of peaceful activities amidst nature.%s", budget, stressNote));
        } else if (mood.contains("spirit")) {
            result.put("destination", "Varanasi, Uttar Pradesh");
            result.put("description", "The spiritual capital of India. Ancient ghats, mesmerizing aarti ceremonies, and deep spiritual energy.");
            result.put("activities", Arrays.asList("🛕 Ganga Aarti at Dashashwamedh", "🚣 Boat ride on Ganges", "🙏 Temple hopping", "🎭 Sarnath Visit", "🧘 Meditation at Assi Ghat"));
            result.put("justification", String.format("We recommend Varanasi because it's the most powerful spiritual destination in India and fits within your budget of ₹%d. The spiritual energy of the Ganges and ancient temples will provide the divine experience you're seeking.%s", budget, stressNote));
        } else if (mood.contains("party")) {
            result.put("destination", "Goa");
            result.put("description", "India's party capital. Beautiful beaches, vibrant nightlife, and endless fun under the sun.");
            result.put("activities", Arrays.asList("🏖️ Beach hopping", "🎵 Night clubs in Baga", "🏄 Water sports", "🌅 Sunset at Chapora Fort", "🍹 Beach shacks"));
            result.put("justification", String.format("We recommend Goa because it's the ultimate party destination in India within your budget of ₹%d. The sunny weather is perfect for beach activities and the vibrant nightlife matches your party mood.", budget));
        } else {
            result.put("destination", "Manali, Himachal Pradesh");
            result.put("description", "Snow-capped mountains, thrilling adventure sports, and stunning Himalayan landscapes await.");
            result.put("activities", Arrays.asList("🪂 Paragliding in Solang Valley", "🏔️ Rohtang Pass Visit", "🏂 Snow Sports", "🌊 River Rafting in Beas", "🛕 Hadimba Temple Trek"));
            result.put("justification", String.format("We recommend Manali because it's the ultimate adventure destination within your budget of ₹%d. The cold weather is perfect for snow activities, crowd is moderate, and the range of adventure sports matches your adventurous spirit.%s", budget, stressNote));
        }

        return result;
    }
}
