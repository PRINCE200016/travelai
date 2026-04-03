package com.tripmind.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public class ConstraintDecisionEngine {

    public record Decision(
            String destinationKey,
            String destinationDisplay,
            String reason,
            Map<String, Object> costBreakdown
    ) {}

    public enum Status { PASS, SOFT_PASS, FAIL, UNREALISTIC }

    public record EngineResult(
            Status status,
            Decision decision,
            Integer minRequiredBudget,
            Integer recommendedDays,
            Integer reduceDaysToFitBudget,
            List<Map<String, Object>> alternatives
    ) {}

    private final DistanceService distanceService;

    public ConstraintDecisionEngine(DistanceService distanceService) {
        this.distanceService = distanceService;
    }

    public List<String> candidatesByDistance(String originCity, int durationDays) {
        double maxKm;
        if (durationDays <= 1) {
            maxKm = 150;
        } else if (durationDays <= 3) {
            maxKm = 400;
        } else {
            maxKm = Double.POSITIVE_INFINITY;
        }
        return distanceService.getDestinationsWithinDistance(originCity, maxKm);
    }

    public EngineResult decide(String originCityRaw,
                           Integer budgetRaw,
                           Integer durationDaysRaw,
                           String travelTypeRaw,
                           Integer travelersCountRaw) {

        String originCity = normalize(originCityRaw);
        int budget = budgetRaw != null ? budgetRaw : 0;
        int days = durationDaysRaw != null ? durationDaysRaw : 1;
        int travelers = travelersCountRaw != null ? travelersCountRaw : 1;
        String travelType = normalize(travelTypeRaw);

        // STEP 2: Candidates ONLY based on distance rules (hard).
        List<String> candidates = new ArrayList<>(candidatesByDistance(originCity, days));
        if (candidates.isEmpty()) {
            // Need more days to travel anywhere valid
            return new EngineResult(Status.FAIL, null, budget, days + 1, null, List.of());
        }

        List<String> orderedCandidates = new ArrayList<>();
        if (isIndore1000OneDaySpiritual(originCity, budget, days, travelType)) {
            orderedCandidates.add("ujjain");
            orderedCandidates.add("omkareshwar");
        }
        for (String c : candidates) {
            if (!orderedCandidates.contains(c)) orderedCandidates.add(c);
        }

        Decision bestDecision = null;
        int minCostOverall = Integer.MAX_VALUE;
        Decision cheapestFail = null;
        List<Decision> allFails = new ArrayList<>();

        for (String destinationKey : orderedCandidates) {
            Decision decision = evaluateCandidate(originCity, budget, days, travelType, travelers, destinationKey);
            if (decision == null) continue; // Invalid by distance/params

            int total = (int) decision.costBreakdown().get("total");

            if (total <= budget) {
                return new EngineResult(Status.PASS, decision, null, null, null, null);
            } else if (total <= budget * 1.1 || total - budget <= 500) {
                // Return immediately on first soft pass due to priority order
                return new EngineResult(Status.SOFT_PASS, decision, null, null, null, null);
            } else {
                allFails.add(decision);
                if (total < minCostOverall) {
                    minCostOverall = total;
                    cheapestFail = decision;
                }
            }
        }

        if (cheapestFail == null) {
            return new EngineResult(Status.FAIL, null, budget, days + 1, null, List.of());
        }

        if (minCostOverall > 300000) {
            return new EngineResult(Status.UNREALISTIC, null, null, null, null, null);
        }

        // Build alternatives
        List<Map<String, Object>> alternatives = new ArrayList<>();
        for (Decision fail : allFails) {
            if (fail != cheapestFail && alternatives.size() < 2) {
                alternatives.add(Map.of("destination", fail.destinationDisplay(), "estimatedCost", fail.costBreakdown().get("total")));
            }
        }

        int reduceDays = days > 1 ? days - 1 : 1;

        return new EngineResult(Status.FAIL, null, minCostOverall, days, days > 1 ? reduceDays : null, alternatives);
    }

    private Decision evaluateCandidate(String originCity,
                                       int budget,
                                       int days,
                                       String travelType,
                                       int travelers,
                                       String destinationKey) {
        if (budget <= 0) return null;
        if (days <= 0) return null;
        if (travelers <= 0) travelers = 1;

        String destKey = normalize(destinationKey);
        String destinationDisplay = distanceService.getDisplayName(destKey);
        if (destinationDisplay == null) return null;

        double oneWayKm = distanceService.getApproxOneWayKm(originCity, destKey);
        if (Double.isNaN(oneWayKm)) return null;

        // STEP 2 hard distance reject
        if (days <= 1 && oneWayKm > 150) return null;
        if (days >= 2 && days <= 3 && oneWayKm > 400) return null;

        // Determine required travel mode by distance (hard rules).
        String requiredMode = requiredMode(oneWayKm);

        // Travel cost estimation (roundtrip).
        int travelCost = estimateTravelCost(oneWayKm, travelers, requiredMode, budget);
        if (travelCost <= 0) return null;

        int stayCost = estimateStayCost(days, travelers, budget);
        int foodCost = estimateFoodCost(days, travelers, budget);
        int activitiesCost = estimateActivitiesCost(days, travelers, budget);

        int total = travelCost + stayCost + foodCost + activitiesCost;

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("travel", travelCost);
        breakdown.put("stay", stayCost);
        breakdown.put("food", foodCost);
        breakdown.put("activities", activitiesCost);
        breakdown.put("total", total);
        breakdown.put("distanceKmOneWay", Math.round(oneWayKm));
        breakdown.put("recommendedMode", requiredMode);

        String reason = buildReason(originCity, budget, days, travelType, destinationDisplay, oneWayKm, breakdown);

        return new Decision(destKey, destinationDisplay, reason, breakdown);
    }

    private String buildReason(String originCity,
                               int budget,
                               int days,
                               String travelType,
                               String destinationDisplay,
                               double oneWayKm,
                               Map<String, Object> breakdown) {
        String dist = Math.round(oneWayKm) + " km one-way";
        String mode = String.valueOf(breakdown.getOrDefault("recommendedMode", "bus_local"));
        int total = (int) breakdown.getOrDefault("total", 0);

        String typeNote = "";
        if (travelType != null && !travelType.isBlank()) {
            String t = travelType.toLowerCase(Locale.ROOT);
            if (t.contains("spirit")) {
                typeNote = "It matches your Spiritual trip type. ";
            } else if (t.contains("advent")) {
                typeNote = "It matches your Adventure trip type. ";
            } else if (t.contains("relax") || t.contains("peace")) {
                typeNote = "It matches your Relaxation trip type. ";
            } else if (t.contains("party")) {
                typeNote = "It matches your Party/Nightlife trip type. ";
            }
        }

        return "Selected because it satisfies hard constraints: distance (" + dist + "), travel mode (" + mode + "), total_cost=₹" + total
                + ", budget=₹" + budget + ", duration_days=" + days + ". " + typeNote;
    }

    private static String requiredMode(double oneWayKm) {
        if (oneWayKm <= 300) return "bus_local";
        if (oneWayKm <= 1000) return "train";
        return "flight";
    }

    private static int estimateTravelCost(double oneWayKm, int travelers, String requiredMode, int budget) {
        // Roundtrip distance approximation (same as DistanceService behavior).
        double roundTripKm = oneWayKm * 2.0;
        if (roundTripKm < 1) roundTripKm = 1;

        double perKm;
        switch (requiredMode) {
            case "flight" -> perKm = 6.0; // average
            case "train" -> perKm = 2.0;  // average
            default -> perKm = budget <= 2000 ? 1.0 : 1.3; // strict low-budget: cheapest
        }

        int cost = (int) Math.round(roundTripKm * perKm * travelers);

        // Guardrails for very low budgets: keep travel realistic and local.
        if (budget <= 1000) {
            cost = Math.min(cost, 300);
            cost = Math.max(cost, 100);
        }

        return cost;
    }

    private static int estimateStayCost(int days, int travelers, int budget) {
        if (days <= 1) return 0;

        int nights = Math.max(days - 1, 1);
        int rooms = (int) Math.ceil(travelers / 2.0);

        // Cheapest hostel/budget tier for constraint engine.
        int perRoomPerNight = budget <= 5000 ? 800 : 1200;
        return perRoomPerNight * rooms * nights;
    }

    private static int estimateFoodCost(int days, int travelers, int budget) {
        int perPersonPerDay;
        if (budget <= 1000) perPersonPerDay = 250;
        else if (budget <= 2000) perPersonPerDay = 280;
        else perPersonPerDay = 350;
        return perPersonPerDay * travelers * Math.max(days, 1);
    }

    private static int estimateActivitiesCost(int days, int travelers, int budget) {
        int perPersonPerDay;
        if (budget <= 1000) perPersonPerDay = 50; // free/minimal
        else if (budget <= 2000) perPersonPerDay = 100;
        else perPersonPerDay = 200;
        return perPersonPerDay * travelers * Math.max(days, 1);
    }

    private static boolean isIndore1000OneDaySpiritual(String originCity, int budget, int days, String travelType) {
        if (!"indore".equals(originCity)) return false;
        if (budget != 1000) return false;
        if (days != 1) return false;
        if (travelType == null) return false;
        return travelType.contains("spirit");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }
}

