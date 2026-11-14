package com.tripmind.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static travel data: origin cities and the destination catalogue. Loaded once from
 * {@code classpath:catalog/*.json} so content can be edited without touching code.
 */
@Component
public class Catalog {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Origin(String name, double lat, double lon, boolean airport, List<String> aliases) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Destination(
            String id, String name, String state, double lat, double lon,
            String terrain, Map<String, Double> tags,
            double costIndex, int activityPerDay, int popularity,
            String railhead, Integer railKm, String airport, Integer airportKm,
            int[] highC, int[] lowC,
            Set<Integer> best, Set<Integer> avoid, Set<Integer> peak, Set<Integer> closed, Set<Integer> roadClosed,
            int minDays, String image, String imageAlt, String summary,
            List<String> highlights, List<String> cautions, boolean airportMinor,
            // Set for AI-discovered places; null for catalogue entries.
            String kind, String localTip, String source, String whyItFits) {

        private static final java.util.regex.Pattern MONTH_PREFIX = java.util.regex.Pattern.compile("^\\[([0-9, ]+)]\\s*(.*)$");

        /** "famous", "offbeat" (hidden gem) or "popular". */
        public String discoveryKind() {
            if (kind != null) return kind;
            return popularity >= 3 ? "famous" : popularity <= 1 ? "offbeat" : "popular";
        }
        public boolean isAi() { return "ai".equals(source); }

        public double tag(String vibe) {
            Double v = tags == null ? null : tags.get(vibe);
            return v == null ? 0 : v;
        }
        public boolean isMountainous() { return "mountain".equals(terrain) || "hills".equals(terrain); }
        public boolean isIsland() { return "island".equals(terrain); }
        public boolean isBeach() { return tag("beach") >= 0.7; }

        /**
         * Cautions relevant to a travel month. Entries may start with "[6,7,8] " to apply only in those months;
         * pass null for every caution (prefixes stripped).
         */
        public List<String> cautionsFor(Integer month) {
            List<String> out = new ArrayList<>();
            if (cautions == null) return out;
            for (String c : cautions) {
                java.util.regex.Matcher m = MONTH_PREFIX.matcher(c);
                if (!m.matches()) { out.add(c); continue; }
                boolean applies = month == null;
                for (String part : m.group(1).split(",")) {
                    if (!part.isBlank() && month != null && Integer.parseInt(part.trim()) == month) applies = true;
                }
                if (applies) out.add(m.group(2));
            }
            return out;
        }
    }

    private final List<Origin> origins;
    private final List<Destination> destinations;
    private final Map<String, Destination> byId = new LinkedHashMap<>();
    private final Map<String, Origin> originIndex = new HashMap<>();

    public Catalog(ObjectMapper mapper) throws IOException {
        this.origins = read(mapper, "catalog/origins.json", new TypeReference<>() {});
        this.destinations = read(mapper, "catalog/destinations.json", new TypeReference<>() {});
        for (Destination d : destinations) {
            validate(d);
            byId.put(d.id(), d);
        }
        for (Origin o : origins) {
            originIndex.put(key(o.name()), o);
            if (o.aliases() != null) o.aliases().forEach(a -> originIndex.put(key(a), o));
        }
    }

    private static <T> List<T> read(ObjectMapper mapper, String path, TypeReference<List<T>> type) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return List.copyOf(mapper.readValue(in, type));
        }
    }

    private static void validate(Destination d) {
        if (d.highC() == null || d.highC().length != 12 || d.lowC() == null || d.lowC().length != 12) {
            throw new IllegalStateException("Destination " + d.id() + " needs 12 monthly temperatures");
        }
        if (d.highlights() == null || d.highlights().isEmpty()) {
            throw new IllegalStateException("Destination " + d.id() + " needs highlights");
        }
    }

    public List<Destination> destinations() { return destinations; }

    /** Nearest city with a commercial airport, for travellers starting from a town without one. */
    public Optional<Origin> nearestAirportHub(double lat, double lon) {
        return origins.stream().filter(Origin::airport)
                .min(Comparator.comparingDouble(o -> haversineKm(lat, lon, o.lat(), o.lon())));
    }
    public Optional<Destination> destination(String id) { return Optional.ofNullable(byId.get(id)); }

    public List<String> originNames() {
        return origins.stream().map(Origin::name).sorted().toList();
    }

    /** Resolves free-text city input (typos, old names, "Pune, Maharashtra") to a known origin. */
    public Optional<Origin> resolveOrigin(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String k = key(raw);
        Origin hit = originIndex.get(k);
        if (hit != null) return Optional.of(hit);

        // "Pune, Maharashtra" / "Kothrud Pune" → try each word and the first comma part
        String firstPart = key(raw.split(",")[0]);
        if (originIndex.containsKey(firstPart)) return Optional.of(originIndex.get(firstPart));
        for (String token : k.split(" ")) {
            if (token.length() >= 4 && originIndex.containsKey(token)) return Optional.of(originIndex.get(token));
        }

        // Typos: unique close match by edit distance (scaled to word length).
        int limit = k.length() <= 5 ? 1 : 2;
        Origin best = null;
        int bestDist = Integer.MAX_VALUE;
        boolean tie = false;
        for (Map.Entry<String, Origin> e : originIndex.entrySet()) {
            int dist = levenshtein(k, e.getKey());
            if (dist < bestDist) { bestDist = dist; best = e.getValue(); tie = false; }
            else if (dist == bestDist && best != e.getValue()) { tie = true; }
        }
        if (best != null && bestDist <= limit && !tie && k.length() >= 4) return Optional.of(best);
        return Optional.empty();
    }

    /** Closest city names to an unknown input, for "did you mean" prompts. */
    public List<String> suggestOrigins(String raw, int limit) {
        String k = key(raw == null ? "" : raw);
        List<Map.Entry<String, Integer>> scored = new ArrayList<>();
        for (Origin o : origins) {
            int d = levenshtein(k, key(o.name()));
            if (o.aliases() != null) for (String a : o.aliases()) d = Math.min(d, levenshtein(k, key(a)));
            scored.add(Map.entry(o.name(), d));
        }
        scored.sort(Comparator.comparingInt(Map.Entry::getValue));
        return scored.stream().limit(limit).map(Map.Entry::getKey).toList();
    }

    static String key(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** Great-circle distance in km. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
