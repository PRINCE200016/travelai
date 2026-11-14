package com.tripmind.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Planner input as sent by the client. Call {@link #normalise(LocalDate)} before use. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanRequest {

    public static final Set<String> VIBES = Set.of("relax", "adventure", "spiritual", "nightlife", "heritage",
            "nature", "beach", "hills", "food", "wildlife");
    public static final int MAX_DAYS = 21;
    public static final int MAX_TRAVELLERS = 20;

    private String origin;
    private String startDate;
    private Integer days;
    private Integer travellers;
    private String group;
    private Integer budget;
    private List<String> vibes;
    private String weather;
    private String crowd;
    private String pace;
    private String comfort;
    private String include;
    private String discovery;
    private String notes;

    // Normalised values (populated by normalise)
    private transient LocalDate start;
    private transient boolean dateAssumed;
    private transient List<Catalog.Destination> pool;

    /**
     * Validates and clamps input. Returns an error message for the user, or null when usable.
     */
    public String normalise(LocalDate today) {
        if (origin == null || origin.trim().length() < 2) return "Tell us which city you're leaving from.";
        origin = origin.trim();
        if (origin.length() > 80) origin = origin.substring(0, 80);

        if (budget == null || budget <= 0) return "Enter a total budget for the trip.";
        if (budget > 5_000_000) budget = 5_000_000;

        days = clamp(days == null ? 3 : days, 1, MAX_DAYS);
        travellers = clamp(travellers == null ? 2 : travellers, 1, MAX_TRAVELLERS);

        group = oneOf(group, Set.of("solo", "couple", "family", "friends"), null);
        if (group == null) group = travellers == 1 ? "solo" : travellers == 2 ? "couple" : "friends";
        if ("solo".equals(group) && travellers > 1) group = "friends";
        if ("couple".equals(group) && travellers != 2) group = travellers == 1 ? "solo" : "friends";

        Set<String> cleanVibes = new LinkedHashSet<>();
        if (vibes != null) {
            for (String v : vibes) {
                if (v == null) continue;
                String k = v.trim().toLowerCase(Locale.ROOT);
                if (k.equals("party")) k = "nightlife";
                if (k.equals("peace") || k.equals("relaxation")) k = "relax";
                if (k.equals("spirit") || k.equals("temples")) k = "spiritual";
                if (k.equals("culture") || k.equals("cultural")) k = "heritage";
                if (VIBES.contains(k)) cleanVibes.add(k);
                if (cleanVibes.size() == 3) break;
            }
        }
        vibes = new ArrayList<>(cleanVibes);

        weather = oneOf(weather, Set.of("cold", "mild", "warm", "any"), "any");
        crowd = oneOf(crowd, Set.of("quiet", "any", "lively"), "any");
        pace = oneOf(pace, Set.of("easy", "balanced", "packed"), "balanced");
        comfort = oneOf(comfort, Set.of("auto", "budget", "standard", "premium"), "auto");
        discovery = oneOf(discovery, Set.of("mix", "hidden", "famous"), "mix");
        if (notes != null) {
            notes = notes.replaceAll("[\\p{Cntrl}<>]", " ").replaceAll("\\s+", " ").trim();
            if (notes.length() > 300) notes = notes.substring(0, 300);
            if (notes.isEmpty()) notes = null;
        }
        include = include == null || include.isBlank() ? null : include.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");

        start = null;
        if (startDate != null && !startDate.isBlank()) {
            try {
                start = LocalDate.parse(startDate.trim());
            } catch (DateTimeParseException e) {
                return "Use a valid start date.";
            }
            if (start.isBefore(today)) return "The start date is in the past.";
            if (start.isAfter(today.plusMonths(18))) return "We can plan up to 18 months ahead.";
        }
        dateAssumed = start == null;
        if (start == null) start = today.plusDays(21);
        startDate = start.toString();
        return null;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String oneOf(String v, Set<String> allowed, String fallback) {
        if (v == null) return fallback;
        String k = v.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(k) ? k : fallback;
    }

    /** A copy with a few fields changed, used when testing "what if" suggestions. */
    public PlanRequest with(Integer newBudget, Integer newDays, String newComfort) {
        PlanRequest r = new PlanRequest();
        r.origin = origin; r.startDate = startDate; r.days = newDays != null ? newDays : days;
        r.travellers = travellers; r.group = group; r.budget = newBudget != null ? newBudget : budget;
        r.vibes = vibes; r.weather = weather; r.crowd = crowd; r.pace = pace;
        r.comfort = newComfort != null ? newComfort : comfort;
        r.include = include;
        r.discovery = discovery;
        r.pool = pool;
        r.notes = notes;
        r.start = start; r.dateAssumed = dateAssumed;
        return r;
    }

    /** Places to consider for this request; null means the built-in catalogue. */
    public List<Catalog.Destination> pool() { return pool; }
    public void usePool(List<Catalog.Destination> places) { this.pool = places; }

    public LocalDate start() { return start; }
    public boolean dateAssumed() { return dateAssumed; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public Integer getTravellers() { return travellers; }
    public void setTravellers(Integer travellers) { this.travellers = travellers; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public Integer getBudget() { return budget; }
    public void setBudget(Integer budget) { this.budget = budget; }
    public List<String> getVibes() { return vibes; }
    public void setVibes(List<String> vibes) { this.vibes = vibes; }
    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }
    public String getCrowd() { return crowd; }
    public void setCrowd(String crowd) { this.crowd = crowd; }
    public String getPace() { return pace; }
    public void setPace(String pace) { this.pace = pace; }
    public String getDiscovery() { return discovery; }
    public void setDiscovery(String discovery) { this.discovery = discovery; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getInclude() { return include; }
    public void setInclude(String include) { this.include = include; }
    public String getComfort() { return comfort; }
    public void setComfort(String comfort) { this.comfort = comfort; }
}
