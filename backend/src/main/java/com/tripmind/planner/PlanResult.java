package com.tripmind.planner;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/** Response types for {@code POST /api/plan}. */
public final class PlanResult {
    private PlanResult() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            String status, String message, Map<String, Object> query,
            List<Trip> recommendations, List<Trip> stretch, List<Suggestion> suggestions,
            List<String> originSuggestions, Trip requested, String requestedNote) {

        public Response(String status, String message, Map<String, Object> query, List<Trip> recommendations,
                        List<Trip> stretch, List<Suggestion> suggestions, List<String> originSuggestions) {
            this(status, message, query, recommendations, stretch, suggestions, originSuggestions, null, null);
        }

        public Response withRequested(Trip trip, String note) {
            return new Response(status, message, query, recommendations, stretch, suggestions, originSuggestions, trip, note);
        }

        public Response withRecommendations(List<Trip> recs) {
            return new Response(status, message, query, recs, stretch, suggestions, originSuggestions, requested, requestedNote);
        }
    }

    public record Suggestion(String label, Map<String, Object> patch) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Trip(
            String id, String name, String state, String image, String imageAlt, String summary,
            List<String> tags, int matchScore, List<String> matchReasons, List<String> cautions,
            int distanceKm, Transport transport, Cost cost, BudgetFit budgetFit,
            Weather weather, Crowd crowd, List<String> bestMonths, List<String> highlights,
            List<Day> dayPlan, Map<String, String> bookingLinks, String narrative,
            String kind, String localTip, String source, double lat, double lon) {

        public Trip withNarrative(String text) {
            return new Trip(id, name, state, image, imageAlt, summary, tags, matchScore, matchReasons, cautions,
                    distanceKm, transport, cost, budgetFit, weather, crowd, bestMonths, highlights, dayPlan,
                    bookingLinks, text, kind, localTip, source, lat, lon);
        }
    }

    public record Transport(String mode, String label, double oneWayHours, boolean overnight, String note) {}

    public record Cost(int transport, int stay, int food, int local, int activities, int buffer, int total,
                       int perPerson, String comfort, int nights, int rooms, String stayLabel,
                       String seasonLabel) {}

    public record BudgetFit(int budget, int remaining, int extraNeeded, String status) {}

    public record Weather(String month, int lowC, int highC, String label, String source) {}

    public record Crowd(String level, List<String> reasons) {}

    public record Day(int day, String title, List<String> items) {}
}
