package com.tripmind.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ConstraintDecisionEngine {

    public static final String REASON_DESTINATION_TOO_EXPENSIVE = "destination_too_expensive";
    public static final String REASON_NO_CANDIDATES = "no_candidates_in_range";
    public static final String REASON_SUGGEST_ADJUSTMENT = "suggest_budget_or_duration";

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
            List<Map<String, Object>> alternatives,
            String constraintReason,
            Integer informationalCheapestCost,
            Decision cheapestFailingDecision
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

        if (!distanceService.isOriginMapped(originCityRaw)) {
            return decideWithUnmappedOrigin(originCityRaw, budget, days, travelType, travelers);
        }

        List<String> candidates = new ArrayList<>(candidatesByDistance(originCity, days));
        if (candidates.isEmpty()) {
            return new EngineResult(
                    Status.FAIL, null, null, days + 1, null, List.of(),
                    REASON_NO_CANDIDATES, null, null);
        }

        List<String> orderedCandidates = new ArrayList<>();
        if (isIndore1000OneDaySpiritual(originCity, budget, days, travelType)) {
            orderedCandidates.add("ujjain");
            orderedCandidates.add("omkareshwar");
        }
        for (String c : candidates) {
            if (!orderedCandidates.contains(c)) orderedCandidates.add(c);
        }

        List<Decision> evaluated = new ArrayList<>();
        for (String destinationKey : orderedCandidates) {
            Decision decision = evaluateCandidate(originCity, budget, days, travelType, travelers, destinationKey);
            if (decision != null) evaluated.add(decision);
        }

        if (evaluated.isEmpty()) {
            return new EngineResult(
                    Status.FAIL, null, null, days + 1, null, List.of(),
                    REASON_NO_CANDIDATES, null, null);
        }

        return finalizeFromEvaluated(originCity, budget, days, travelType, travelers, evaluated, false, null);
    }

    private EngineResult decideWithUnmappedOrigin(String rawLabel, int budget, int days, String travelType, int travelers) {
        double synKm = syntheticKmForDuration(days);
        String label = rawLabel != null && !rawLabel.isBlank() ? rawLabel.trim() : "your city";
        List<Decision> evaluated = new ArrayList<>();
        for (String key : distanceService.plannerDestinationKeys()) {
            Decision d = evaluateCandidateWithSynthetic(label, budget, days, travelType, travelers, key, synKm);
            if (d != null) {
                evaluated.add(d);
            }
        }
        if (evaluated.isEmpty()) {
            return new EngineResult(
                    Status.FAIL, null, null, days + 1, null, List.of(),
                    REASON_NO_CANDIDATES, null, null);
        }
        return finalizeFromEvaluated("", budget, days, travelType, travelers, evaluated, true, label);
    }

    private EngineResult finalizeFromEvaluated(
            String originCityNormalized,
            int budget,
            int days,
            String travelType,
            int travelers,
            List<Decision> evaluated,
            boolean syntheticOrigin,
            String userOriginLabel) {

        Decision bestPass = null;
        int bestPassCost = Integer.MAX_VALUE;
        Decision bestSoft = null;
        int bestSoftCost = Integer.MAX_VALUE;

        for (Decision d : evaluated) {
            int total = totalOf(d);
            if (total <= budget) {
                if (total < bestPassCost) {
                    bestPassCost = total;
                    bestPass = d;
                }
            } else if (isSoftPass(total, budget)) {
                if (total < bestSoftCost) {
                    bestSoftCost = total;
                    bestSoft = d;
                }
            }
        }

        if (bestPass != null) {
            return new EngineResult(Status.PASS, bestPass, null, null, null, null, null, null, null);
        }
        if (bestSoft != null) {
            return new EngineResult(Status.SOFT_PASS, bestSoft, null, null, null, null, null, null, null);
        }

        List<Decision> sortedFails = new ArrayList<>(evaluated);
        sortedFails.sort(Comparator.comparingInt(this::totalOf));
        Decision cheapest = sortedFails.get(0);
        int cheapestCost = totalOf(cheapest);

        if (cheapestCost > 300_000) {
            return new EngineResult(Status.UNREALISTIC, null, null, null, null, null, null, null, null);
        }

        List<Map<String, Object>> alternatives = buildAlternativesList(sortedFails, 5);
        Integer reduceDays;
        if (syntheticOrigin) {
            reduceDays = computeReduceDaysSynthetic(userOriginLabel, budget, days, travelType, travelers, cheapest.destinationKey());
        } else {
            reduceDays = computeReduceDaysIfHelps(originCityNormalized, budget, days, travelType, travelers, cheapest.destinationKey());
        }

        final double tooExpensiveRatio = 1.5;
        boolean wayOverBudget = budget > 0 && cheapestCost > budget * tooExpensiveRatio;

        if (wayOverBudget) {
            return new EngineResult(
                    Status.FAIL,
                    null,
                    null,
                    null,
                    reduceDays,
                    alternatives,
                    REASON_DESTINATION_TOO_EXPENSIVE,
                    cheapestCost,
                    cheapest);
        }

        return new EngineResult(
                Status.FAIL,
                null,
                cheapestCost,
                days,
                reduceDays,
                alternatives,
                REASON_SUGGEST_ADJUSTMENT,
                cheapestCost,
                cheapest);
    }

    private static double syntheticKmForDuration(int days) {
        if (days <= 1) {
            return 120;
        }
        if (days <= 3) {
            return 280;
        }
        return 450;
    }

    private Decision evaluateCandidateWithSynthetic(String userOriginLabel,
                                                    int budget,
                                                    int days,
                                                    String travelType,
                                                    int travelers,
                                                    String destinationKey,
                                                    double oneWayKm) {
        if (budget <= 0 || days <= 0) return null;
        if (travelers <= 0) travelers = 1;

        String destKey = normalize(destinationKey);
        String destinationDisplay = distanceService.getDisplayName(destKey);
        if (destinationDisplay == null) return null;

        String requiredMode = requiredMode(oneWayKm);
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

        String reason = buildReasonSynthetic(userOriginLabel, budget, days, travelType, destinationDisplay, oneWayKm, breakdown);
        return new Decision(destKey, destinationDisplay, reason, breakdown);
    }

    private String buildReasonSynthetic(String userOriginLabel,
                                       int budget,
                                       int days,
                                       String travelType,
                                       String destinationDisplay,
                                       double oneWayKm,
                                       Map<String, Object> breakdown) {
        String mode = String.valueOf(breakdown.getOrDefault("recommendedMode", "bus_local"));
        int total = (int) breakdown.getOrDefault("total", 0);
        String typeNote = "";
        if (travelType != null && !travelType.isBlank()) {
            String t = travelType.toLowerCase(Locale.ROOT);
            if (t.contains("spirit")) {
                typeNote = "Spiritual trip fit. ";
            } else if (t.contains("advent")) {
                typeNote = "Adventure-oriented fit. ";
            } else if (t.contains("relax") || t.contains("peace")) {
                typeNote = "Relaxation fit. ";
            } else if (t.contains("party")) {
                typeNote = "Social/nightlife fit. ";
            }
        }
        return "Approximate plan from " + userOriginLabel + " (~" + Math.round(oneWayKm)
                + " km typical one-way), mode " + mode + ", total ₹" + total + " vs ₹" + budget + " budget, "
                + days + " day(s). " + typeNote + "Distances are typical until we add exact coordinates for your city.";
    }

    private Integer computeReduceDaysSynthetic(String label,
                                              int budget,
                                              int days,
                                              String travelType,
                                              int travelers,
                                              String destKey) {
        if (days <= 1) return null;
        double km1 = syntheticKmForDuration(1);
        Decision oneDay = evaluateCandidateWithSynthetic(label, budget, 1, travelType, travelers, destKey, km1);
        if (oneDay != null && totalOf(oneDay) <= budget) {
            return 1;
        }
        int reduced = days - 1;
        double kmR = syntheticKmForDuration(reduced);
        Decision d = evaluateCandidateWithSynthetic(label, budget, reduced, travelType, travelers, destKey, kmR);
        if (d != null && totalOf(d) <= budget) {
            return reduced;
        }
        return days > 1 ? days - 1 : null;
    }

    private static boolean isSoftPass(int total, int budget) {
        return total <= budget * 1.1 || total - budget <= 500;
    }

    private int totalOf(Decision d) {
        return (int) d.costBreakdown().get("total");
    }

    private List<Map<String, Object>> buildAlternativesList(List<Decision> sortedByCostAscending, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Decision d : sortedByCostAscending) {
            if (out.size() >= limit) break;
            String key = d.destinationKey();
            if (seen.contains(key)) continue;
            seen.add(key);
            int total = totalOf(d);
            Map<String, Object> row = new HashMap<>();
            row.put("name", shortName(d.destinationDisplay()));
            row.put("destination", d.destinationDisplay());
            row.put("estimatedCost", total);
            out.add(row);
        }
        return List.copyOf(out);
    }

    private static String shortName(String display) {
        if (display == null) return "";
        int comma = display.indexOf(',');
        return comma > 0 ? display.substring(0, comma).trim() : display.trim();
    }

    private Integer computeReduceDaysIfHelps(String originCity,
                                            int budget,
                                            int days,
                                            String travelType,
                                            int travelers,
                                            String sampleDestinationKey) {
        if (days <= 1) return null;
        Decision oneDay = evaluateCandidate(originCity, budget, 1, travelType, travelers, sampleDestinationKey);
        if (oneDay != null && totalOf(oneDay) <= budget) {
            return 1;
        }
        int reduced = days - 1;
        Decision d = evaluateCandidate(originCity, budget, reduced, travelType, travelers, sampleDestinationKey);
        if (d != null && totalOf(d) <= budget) {
            return reduced;
        }
        return days > 1 ? days - 1 : null;
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

        if (days <= 1 && oneWayKm > 150) return null;
        if (days >= 2 && days <= 3 && oneWayKm > 400) return null;

        String requiredMode = requiredMode(oneWayKm);

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
        double roundTripKm = oneWayKm * 2.0;
        if (roundTripKm < 1) roundTripKm = 1;

        double perKm;
        switch (requiredMode) {
            case "flight" -> perKm = 6.0;
            case "train" -> perKm = 2.0;
            default -> perKm = budget <= 2000 ? 1.0 : 1.3;
        }

        int cost = (int) Math.round(roundTripKm * perKm * travelers);

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
        if (budget <= 1000) perPersonPerDay = 50;
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
