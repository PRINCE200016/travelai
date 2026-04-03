package com.tripmind.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DistanceService {
    private static final Logger log = LoggerFactory.getLogger(DistanceService.class);

    // Rates per km
    private static final double RATE_FLIGHT_MIN = 4.0;
    private static final double RATE_FLIGHT_MAX = 8.0;
    private static final double RATE_TRAIN_MIN = 1.5;
    private static final double RATE_TRAIN_MAX = 3.0;
    private static final double RATE_BUS_MIN = 1.0;
    private static final double RATE_BUS_MAX = 2.0;

    // Hardcoded coordinates for major Indian cities (Fallback since API keys aren't guaranteed)
    private static final Map<String, Coordinate> CITIES = new HashMap<>();
    static {
        CITIES.put("delhi", new Coordinate(28.6139, 77.2090));
        CITIES.put("mumbai", new Coordinate(19.0760, 72.8777));
        CITIES.put("bangalore", new Coordinate(12.9716, 77.5946));
        CITIES.put("bengaluru", new Coordinate(12.9716, 77.5946));
        CITIES.put("hyderabad", new Coordinate(17.3850, 78.4867));
        CITIES.put("chennai", new Coordinate(13.0827, 80.2707));
        CITIES.put("kolkata", new Coordinate(22.5726, 88.3639));
        CITIES.put("pune", new Coordinate(18.5204, 73.8567));
        CITIES.put("ahmedabad", new Coordinate(23.0225, 72.5714));
        CITIES.put("jaipur", new Coordinate(26.9124, 75.7873));
        CITIES.put("lucknow", new Coordinate(26.8467, 80.9462));
        CITIES.put("kanpur", new Coordinate(26.4499, 80.3319));
        CITIES.put("nagpur", new Coordinate(21.1458, 79.0882));
        CITIES.put("indore", new Coordinate(22.7196, 75.8577));
        CITIES.put("ujjain", new Coordinate(23.1765, 75.7885));
        CITIES.put("omkareshwar", new Coordinate(22.2419, 76.1510));
        CITIES.put("bhopal", new Coordinate(23.2599, 77.4126));
        CITIES.put("patna", new Coordinate(25.5941, 85.1376));
        CITIES.put("vadodara", new Coordinate(22.3072, 73.1812));
        CITIES.put("manali", new Coordinate(32.2396, 77.1887));
        CITIES.put("goa", new Coordinate(15.2993, 74.1240));
        CITIES.put("munnar", new Coordinate(10.0889, 77.0595));
        CITIES.put("varanasi", new Coordinate(25.3176, 83.0064));
        CITIES.put("rishikesh", new Coordinate(30.0869, 78.2676));
        CITIES.put("kashmir", new Coordinate(34.0837, 74.7973));
        CITIES.put("srinagar", new Coordinate(34.0837, 74.7973));
        CITIES.put("udaipur", new Coordinate(24.5854, 73.7125));
        CITIES.put("agra", new Coordinate(27.1767, 78.0081));
        CITIES.put("gwalior", new Coordinate(26.2124, 78.1772));
    }

    private static final Map<String, String> ORIGIN_ALIASES = new HashMap<>();

    static {
        ORIGIN_ALIASES.put("dehli", "delhi");
        ORIGIN_ALIASES.put("delli", "delhi");
        ORIGIN_ALIASES.put("dilli", "delhi");
        ORIGIN_ALIASES.put("newdelhi", "delhi");
        ORIGIN_ALIASES.put("new-delhi", "delhi");
        ORIGIN_ALIASES.put("bombay", "mumbai");
        ORIGIN_ALIASES.put("calcutta", "kolkata");
        ORIGIN_ALIASES.put("bengluru", "bengaluru");
        ORIGIN_ALIASES.put("bengalooru", "bengaluru");
    }

    /**
     * Maps user-entered origin text to a key in {@link #CITIES}. Handles common misspellings
     * (e.g. {@code dehli} → {@code delhi}) so the chat flow can always return a destination.
     */
    public String resolveOriginToRegistryKey(String rawOrigin) {
        if (rawOrigin == null || rawOrigin.isBlank()) {
            return null;
        }
        String normalized = rawOrigin.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        normalized = normalized.replaceAll(",\\s*", " ").trim();
        String compact = normalized.replace(" ", "");

        if ("new delhi".equals(normalized)) {
            return "delhi";
        }
        if (ORIGIN_ALIASES.containsKey(normalized)) {
            return ORIGIN_ALIASES.get(normalized);
        }
        if (ORIGIN_ALIASES.containsKey(compact)) {
            return ORIGIN_ALIASES.get(compact);
        }
        if (CITIES.containsKey(normalized)) {
            return normalized;
        }
        if (CITIES.containsKey(compact)) {
            return compact;
        }
        for (String key : CITIES.keySet()) {
            if (normalized.contains(key) || key.contains(normalized)) {
                return key;
            }
        }
        return uniqueCloseLevenshteinMatch(normalized, 2);
    }

    private static String uniqueCloseLevenshteinMatch(String input, int maxDist) {
        if (input.length() < 4) {
            return null;
        }
        List<String> hits = new ArrayList<>();
        for (String city : CITIES.keySet()) {
            if (levenshtein(input, city) <= maxDist) {
                hits.add(city);
            }
        }
        if (hits.size() != 1) {
            return null;
        }
        return hits.get(0);
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] row = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            row[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            row[0] = i;
            int prev = i - 1;
            for (int j = 1; j <= m; j++) {
                int tmp = row[j];
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), prev + cost);
                prev = tmp;
            }
        }
        return row[m];
    }

    /** True when we can place the user's origin on the map (geo candidates + distance checks apply). */
    public boolean isOriginMapped(String rawOrigin) {
        return resolveOriginToRegistryKey(rawOrigin) != null;
    }

    /** All internal destination keys for nationwide feasibility search (e.g. unknown origin). */
    public List<String> plannerDestinationKeys() {
        return new java.util.ArrayList<>(CITIES.keySet());
    }

    public List<String> getDestinationsWithinDistance(String origin, double maxOneWayKm) {
        List<String> result = new java.util.ArrayList<>();
        if (origin == null || origin.trim().isEmpty()) return result;

        String originKey = resolveOriginToRegistryKey(origin);
        if (originKey == null) return result;
        Coordinate originCoord = CITIES.get(originKey);
        if (originCoord == null) return result;

        for (Map.Entry<String, Coordinate> entry : CITIES.entrySet()) {
            String city = entry.getKey();
            if (city.equalsIgnoreCase(originKey)) continue;
            double dist = calculateHaversine(originCoord.lat, originCoord.lon, entry.getValue().lat, entry.getValue().lon);
            dist = dist * 1.15;
            if (dist <= maxOneWayKm) {
                result.add(city);
            }
        }
        // Stable ordering: nearest first
        result.sort(Comparator.comparingDouble(c -> {
            double km = getApproxOneWayKm(origin, c);
            return Double.isNaN(km) ? Double.POSITIVE_INFINITY : km;
        }));
        return result;
    }

    public double getApproxOneWayKm(String origin, String destinationKey) {
        if (origin == null || destinationKey == null) return Double.NaN;
        Coordinate originCoord = getClosestCity(origin.toLowerCase().trim());
        Coordinate destCoord = getClosestCity(destinationKey.toLowerCase().trim());
        if (originCoord == null || destCoord == null) return Double.NaN;
        // Match getDestinationsWithinDistance: use raw haversine × 1.15 (no intermediate round),
        // so eligibility in the candidate list and evaluateCandidate distance checks stay aligned.
        double distance = calculateHaversine(originCoord.lat, originCoord.lon, destCoord.lat, destCoord.lon);
        return distance * 1.15;
    }

    public String getDisplayName(String destinationKey) {
        if (destinationKey == null) return null;
        String key = destinationKey.toLowerCase().trim();
        // Minimal mapping for key destinations. Fallback to Title Case key.
        return switch (key) {
            case "ujjain" -> "Ujjain, Madhya Pradesh";
            case "omkareshwar" -> "Omkareshwar, Madhya Pradesh";
            case "indore" -> "Indore, Madhya Pradesh";
            case "manali" -> "Manali, Himachal Pradesh";
            case "goa" -> "Goa";
            case "munnar" -> "Munnar, Kerala";
            case "varanasi" -> "Varanasi, Uttar Pradesh";
            case "rishikesh" -> "Rishikesh, Uttarakhand";
            case "kashmir", "srinagar" -> "Srinagar, Jammu & Kashmir";
            case "jaipur" -> "Jaipur, Rajasthan";
            case "gwalior" -> "Gwalior, Madhya Pradesh";
            default -> {
                if (!CITIES.containsKey(key)) yield null;
                yield Character.toUpperCase(key.charAt(0)) + key.substring(1);
            }
        };
    }

    public Map<String, Object> calculateTravelCost(String origin, String destination, int travelers) {
        Map<String, Object> result = new HashMap<>();

        if (origin == null || destination == null) {
            return getFallbackTravelCost(travelers);
        }

        String oKey = origin.toLowerCase().trim();
        String dKey = destination.split(",")[0].toLowerCase().trim();

        Coordinate originCoord = getClosestCity(oKey);
        Coordinate destCoord = getClosestCity(dKey);

        if (originCoord == null || destCoord == null) {
            log.info("Could not map origin [{}] or destination [{}] to known coordinates. Using fallback.", origin, destination);
            return getFallbackTravelCost(travelers);
        }

        // Calculate Haversine distance in KM
        double distance = calculateHaversine(originCoord.lat, originCoord.lon, destCoord.lat, destCoord.lon);
        distance = Math.round(distance);

        // Add 15% to straight line distance to approximate driving/train distance
        distance = distance * 1.15;

        // Same city logic
        if (distance < 50) {
            result.put("distanceInfo", "Less than 50 km");
            result.put("flight", "N/A");
            result.put("train", "N/A");
            result.put("bus", "₹" + format((int)(50 * RATE_BUS_MIN * travelers)) + " - ₹" + format((int)(100 * RATE_BUS_MAX * travelers)));
            result.put("recommendedMode", "Taxi/Local Bus");
            result.put("estimatedTravelCost", (int)(75 * travelers)); // Average cost
            return result;
        }

        // Costs calculation (Roundtrip = distance * 2)
        double totalDist = distance * 2;

        int flightMin = (int)(totalDist * RATE_FLIGHT_MIN * travelers);
        int flightMax = (int)(totalDist * RATE_FLIGHT_MAX * travelers);

        int trainMin = (int)(totalDist * RATE_TRAIN_MIN * travelers);
        int trainMax = (int)(totalDist * RATE_TRAIN_MAX * travelers);

        int busMin = (int)(totalDist * RATE_BUS_MIN * travelers);
        int busMax = (int)(totalDist * RATE_BUS_MAX * travelers);

        result.put("distanceInfo", Math.round(distance) + " km (One way)");
        result.put("flight", distance > 300 ? "₹" + format(flightMin) + " - ₹" + format(flightMax) : "N/A");
        result.put("train", "₹" + format(trainMin) + " - ₹" + format(trainMax));
        result.put("bus", "₹" + format(busMin) + " - ₹" + format(busMax));

        // Determine recommended mode based on distance
        if (distance > 1000) {
            result.put("recommendedMode", "Flight");
            result.put("estimatedTravelCost", (flightMin + flightMax) / 2);
        } else if (distance > 300) {
            result.put("recommendedMode", "Train");
            result.put("estimatedTravelCost", (trainMin + trainMax) / 2);
        } else {
            result.put("recommendedMode", "Bus/Car");
            result.put("estimatedTravelCost", (busMin + busMax) / 2);
        }

        return result;
    }

    private Coordinate getClosestCity(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String key = resolveOriginToRegistryKey(input);
        return key != null ? CITIES.get(key) : null;
    }

    private double calculateHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in KM
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private Map<String, Object> getFallbackTravelCost(int travelers) {
        Map<String, Object> result = new HashMap<>();
        result.put("distanceInfo", "Not Available");
        result.put("flight", "₹" + format(4000 * travelers) + " - ₹" + format(8000 * travelers));
        result.put("train", "₹" + format(1500 * travelers) + " - ₹" + format(3000 * travelers));
        result.put("bus", "₹" + format(800 * travelers) + " - ₹" + format(1500 * travelers));
        result.put("recommendedMode", "Train / Flight");
        result.put("estimatedTravelCost", 3500 * travelers); // Average fallback
        return result;
    }

    private String format(int num) {
        return String.format("%,d", num);
    }

    public java.util.List<String> getValidDestinations(String origin, double budget, int duration, int travelers) {
        java.util.List<String> validDestinations = new java.util.ArrayList<>();
        if (origin == null || origin.trim().isEmpty()) {
            return new java.util.ArrayList<>(CITIES.keySet());
        }

        Coordinate originCoord = getClosestCity(origin.toLowerCase().trim());
        if (originCoord == null) {
            return new java.util.ArrayList<>(CITIES.keySet());
        }

        double minHotelCostPerNight = 800 * travelers;
        double minFoodCostPerDay = 400 * travelers;
        double minActivityCost = 200 * travelers;

        double hotelCost = (duration > 1) ? minHotelCostPerNight * (duration - 1) : 0;
        double foodCost = minFoodCostPerDay * duration;
        double baseCostBeforeTravel = hotelCost + foodCost + minActivityCost;

        for (Map.Entry<String, Coordinate> entry : CITIES.entrySet()) {
            String city = entry.getKey();
            Coordinate destCoord = entry.getValue();

            double distance = calculateHaversine(originCoord.lat, originCoord.lon, destCoord.lat, destCoord.lon);
            distance = distance * 1.15; // adding 15% for road approximation

            // Distance rules
            if (duration == 1 && distance > 150) continue;
            if (duration <= 3 && distance > 400) continue;

            // Cost rules
            double minTravelCost = 0;
            if (distance > 50) {
                // Return trip by cheapest mode
                minTravelCost = distance * 2 * RATE_BUS_MIN * travelers;
            } else {
                minTravelCost = 50 * RATE_BUS_MIN * travelers;
            }

            if ((baseCostBeforeTravel + minTravelCost) <= budget) {
                validDestinations.add(city);
            }
        }

        return validDestinations;
    }

    private record Coordinate(double lat, double lon) {}
}
