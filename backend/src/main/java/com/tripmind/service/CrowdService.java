package com.tripmind.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;

@Service
public class CrowdService {

    // Festival calendar (month -> festivals)
    private static final Map<Month, List<String>> FESTIVALS = Map.ofEntries(
            Map.entry(Month.JANUARY, List.of("Republic Day", "Makar Sankranti")),
            Map.entry(Month.MARCH, List.of("Holi")),
            Map.entry(Month.APRIL, List.of("Ram Navami", "Easter")),
            Map.entry(Month.AUGUST, List.of("Independence Day", "Raksha Bandhan")),
            Map.entry(Month.OCTOBER, List.of("Dussehra", "Navratri")),
            Map.entry(Month.NOVEMBER, List.of("Diwali", "Chhath Puja")),
            Map.entry(Month.DECEMBER, List.of("Christmas", "New Year Eve"))
    );

    // Peak seasons by destination type
    private static final Map<String, Set<Month>> PEAK_SEASONS = Map.of(
            "hill_station", Set.of(Month.APRIL, Month.MAY, Month.JUNE, Month.OCTOBER, Month.DECEMBER),
            "beach", Set.of(Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY),
            "spiritual", Set.of(Month.OCTOBER, Month.NOVEMBER, Month.MARCH, Month.APRIL),
            "city", Set.of(Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH)
    );

    // Destination type mapping
    private static final Map<String, String> DESTINATION_TYPES = Map.ofEntries(
            Map.entry("manali", "hill_station"),
            Map.entry("shimla", "hill_station"),
            Map.entry("munnar", "hill_station"),
            Map.entry("ooty", "hill_station"),
            Map.entry("darjeeling", "hill_station"),
            Map.entry("kashmir", "hill_station"),
            Map.entry("nainital", "hill_station"),
            Map.entry("goa", "beach"),
            Map.entry("kovalam", "beach"),
            Map.entry("pondicherry", "beach"),
            Map.entry("andaman", "beach"),
            Map.entry("varanasi", "spiritual"),
            Map.entry("ujjain", "spiritual"),
            Map.entry("rishikesh", "spiritual"),
            Map.entry("tirupati", "spiritual"),
            Map.entry("haridwar", "spiritual"),
            Map.entry("delhi", "city"),
            Map.entry("mumbai", "city"),
            Map.entry("jaipur", "city"),
            Map.entry("bangalore", "city"),
            Map.entry("kolkata", "city")
    );

    public Map<String, String> getCrowdLevel(String destination) {
        LocalDate today = LocalDate.now();
        String destKey = destination.toLowerCase().split(",")[0].trim();
        String destType = DESTINATION_TYPES.getOrDefault(destKey, "city");

        int crowdScore = 0;
        List<String> reasons = new ArrayList<>();

        // 1. Check if peak season
        Set<Month> peaks = PEAK_SEASONS.getOrDefault(destType, Set.of());
        if (peaks.contains(today.getMonth())) {
            crowdScore += 3;
            reasons.add("peak tourist season for " + destType.replace("_", " ") + "s");
        }

        // 2. Check weekend
        DayOfWeek dow = today.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            crowdScore += 2;
            reasons.add("weekend");
        } else if (dow == DayOfWeek.FRIDAY) {
            crowdScore += 1;
            reasons.add("pre-weekend Friday");
        }

        // 3. Check festivals
        List<String> festivals = FESTIVALS.getOrDefault(today.getMonth(), List.of());
        if (!festivals.isEmpty()) {
            crowdScore += 2;
            reasons.add(festivals.get(0) + " season");
        }

        // 4. School vacation months
        if (today.getMonth() == Month.MAY || today.getMonth() == Month.JUNE) {
            crowdScore += 2;
            reasons.add("school vacation period");
        }

        // Determine level
        String level;
        if (crowdScore >= 5) {
            level = "High";
        } else if (crowdScore >= 2) {
            level = "Medium";
        } else {
            level = "Low";
        }

        String reason = reasons.isEmpty() ? "Regular period — relatively quiet" : String.join(", ", reasons);
        reason = capitalize(reason);

        Map<String, String> result = new HashMap<>();
        result.put("level", level);
        result.put("reason", reason);
        return result;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
