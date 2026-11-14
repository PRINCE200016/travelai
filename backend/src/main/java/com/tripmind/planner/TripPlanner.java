package com.tripmind.planner;

import com.tripmind.planner.Catalog.Destination;
import com.tripmind.planner.Catalog.Origin;
import com.tripmind.planner.PlanResult.BudgetFit;
import com.tripmind.planner.PlanResult.Cost;
import com.tripmind.planner.PlanResult.Crowd;
import com.tripmind.planner.PlanResult.Day;
import com.tripmind.planner.PlanResult.Response;
import com.tripmind.planner.PlanResult.Suggestion;
import com.tripmind.planner.PlanResult.Transport;
import com.tripmind.planner.PlanResult.Trip;
import com.tripmind.planner.PlanResult.Weather;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The trip planner. For every catalogue destination it:
 * <ol>
 *   <li>picks a realistic way to get there (road / train / flight) for the distance, comfort level and time,</li>
 *   <li>rejects it if the journey doesn't leave enough time on the ground, or it's closed that month,</li>
 *   <li>costs the whole trip (transport, stay, food, local transport, activities, a small buffer),</li>
 *   <li>scores how well it matches the traveller (trip type, season, weather, crowds, travel effort).</li>
 * </ol>
 * Only trips whose full cost is within budget are recommended. Nothing is changed on the user's behalf;
 * when nothing fits, concrete re-plan suggestions are returned instead.
 */
@Service
public class TripPlanner {

    static final String[] COMFORTS = {"budget", "standard", "premium"};
    /** Average tag strength across the requested trip types needed to count as "offers what you asked for". */
    static final double MIN_VIBE_MATCH = 0.45;

    private final Catalog catalog;

    public TripPlanner(Catalog catalog) {
        this.catalog = catalog;
    }

    // ------------------------------------------------------------------ entry point

    public Response plan(PlanRequest req) {
        Optional<Origin> originOpt = catalog.resolveOrigin(req.getOrigin());
        if (originOpt.isEmpty()) return originUnknown(req);
        return plan(req, originOpt.get());
    }

    public Response originUnknown(PlanRequest req) {
        return new Response("origin_unknown",
                "We couldn't find \u201c" + req.getOrigin() + "\u201d on the map. Check the spelling or pick a nearby town.",
                queryEcho(req, null), List.of(), null, List.of(), catalog.suggestOrigins(req.getOrigin(), 4));
    }

    /** Plans from a resolved origin over {@code req.pool()} (or the catalogue when no pool is set). */
    public Response plan(PlanRequest req, Origin origin) {
        List<Candidate> all = evaluateAll(origin, req);
        Response res = rank(origin, req, all);
        return req.getInclude() == null ? res : attachRequested(res, origin, req, all);
    }

    private Response rank(Origin origin, PlanRequest req, List<Candidate> all) {

        List<Candidate> fits = all.stream().filter(c -> c.total <= req.getBudget()).toList();

        // When the traveller named a kind of trip, only recommend places that genuinely offer it.
        if (!req.getVibes().isEmpty() && !fits.isEmpty()) {
            List<Candidate> matching = fits.stream().filter(c -> c.vibe >= MIN_VIBE_MATCH).toList();
            if (!matching.isEmpty()) {
                fits = matching;
            } else {
                return mismatch(origin, req, all, fits);
            }
        }

        if (!fits.isEmpty()) {
            List<Candidate> top = ensureHiddenGem(pickDiverse(fits, 3), fits, req);
            int bestScore = top.get(0).score;
            // Better matches a little over budget are shown separately, never mixed in.
            List<Candidate> stretch = all.stream()
                    .filter(c -> c.total > req.getBudget())
                    .filter(c -> c.score >= bestScore + 8)
                    .filter(c -> c.total - req.getBudget() <= Math.max(3000, req.getBudget() * 0.3))
                    .sorted(Comparator.comparingInt((Candidate c) -> c.score).reversed())
                    .limit(2).toList();
            String msg = top.size() + (top.size() == 1 ? " trip fits " : " trips fit ") + inr(req.getBudget())
                    + " for " + people(req.getTravellers()) + ".";
            return new Response("ok", msg, queryEcho(req, origin),
                    top.stream().map(c -> toTrip(c, origin, req)).toList(),
                    stretch.stream().map(c -> toTrip(c, origin, req)).toList(),
                    List.of(), null);
        }

        if (!all.isEmpty()) {
            List<Candidate> stretch = new ArrayList<>(all);
            stretch.sort(Comparator.comparingDouble((Candidate c) ->
                    c.score - 40.0 * (c.total - req.getBudget()) / Math.max(1, req.getBudget())).reversed());
            List<Candidate> shown = pickDiverse(stretch, 3);
            Candidate cheapest = all.stream().min(Comparator.comparingInt(c -> c.total)).orElseThrow();
            String msg = "Nothing we know of fits " + inr(req.getBudget()) + " for " + people(req.getTravellers())
                    + " over " + days(req.getDays()) + ". The most affordable option, " + cheapest.d.name()
                    + ", comes to about " + inr(cheapest.total) + ".";
            return new Response("over_budget", msg, queryEcho(req, origin), List.of(),
                    shown.stream().map(c -> toTrip(c, origin, req)).toList(),
                    suggestionsWhenOver(origin, req, shown.get(0), cheapest), null);
        }

        return new Response("no_match",
                "From " + origin.name() + ", " + days(req.getDays()) + " isn't enough to reach anywhere in our catalogue and back with time to enjoy it.",
                queryEcho(req, origin), List.of(), List.of(), suggestionsWhenNoMatch(origin, req), null);
    }

    /**
     * Trips fit the budget, but none offer what was asked for. Say so, show what the matching places would
     * cost, and only then offer the in-budget trips as alternatives.
     */
    private Response mismatch(Origin origin, PlanRequest req, List<Candidate> all, List<Candidate> fits) {
        String wanted = joinWords(req.getVibes().stream().map(TripPlanner::vibePhrase).toList());
        List<Candidate> matchingOver = all.stream()
                .filter(c -> c.vibe >= MIN_VIBE_MATCH && c.total > req.getBudget())
                .sorted(Comparator.comparingDouble((Candidate c) -> c.score - 30.0 * (c.total - req.getBudget()) / Math.max(1, req.getBudget())).reversed())
                .toList();
        List<Candidate> stretch = pickDiverse(matchingOver, 2);
        List<Suggestion> suggestions = new ArrayList<>();
        if (!stretch.isEmpty()) {
            int need = roundUp(stretch.get(0).total, 500);
            suggestions.add(new Suggestion("Raise the budget to " + inr(need) + " for " + stretch.get(0).d.name(), Map.of("budget", need)));
        }
        for (int nd = req.getDays() + 1; nd <= Math.min(PlanRequest.MAX_DAYS, req.getDays() + 3) && stretch.isEmpty(); nd++) {
            PlanRequest alt = req.with(null, nd, null);
            boolean reachable = evaluateAll(origin, alt).stream().anyMatch(c -> c.vibe >= MIN_VIBE_MATCH);
            if (reachable) suggestions.add(new Suggestion("Plan " + days(nd) + " so places for " + wanted + " are in reach", Map.of("days", nd)));
            if (reachable) break;
        }
        suggestions.add(new Suggestion("Show trips without a trip-type preference", Map.of("vibes", List.of())));

        String msg = "Nothing strong for " + wanted + " fits " + inr(req.getBudget()) + " over " + days(req.getDays())
                + " from " + origin.name() + "."
                + (stretch.isEmpty() ? "" : " The closest, " + stretch.get(0).d.name() + ", comes to about " + inr(stretch.get(0).total) + ".")
                + " These trips do fit your budget.";
        return new Response("ok_other", msg, queryEcho(req, origin),
                pickDiverse(fits, 3).stream().map(c -> toTrip(c, origin, req)).toList(),
                stretch.stream().map(c -> toTrip(c, origin, req)).toList(),
                suggestions, null);
    }

    /** Adds the destination the user specifically asked about, or explains why it isn't possible. */
    private Response attachRequested(Response res, Origin origin, PlanRequest req, List<Candidate> all) {
        Destination d = (req.pool() == null ? List.<Destination>of() : req.pool()).stream()
                .filter(x -> x.id().equals(req.getInclude())).findFirst()
                .orElseGet(() -> catalog.destination(req.getInclude()).orElse(null));
        if (d == null) return res;
        Candidate c = all.stream().filter(x -> x.d == d).findFirst().orElseGet(() -> evaluate(d, origin, req));
        if (c != null) return res.withRequested(toTrip(c, origin, req), null);
        int m = req.start().getMonthValue();
        String why;
        if (Catalog.haversineKm(origin.lat(), origin.lon(), d.lat(), d.lon()) < 40) why = d.name() + " is where you're starting from.";
        else if (d.closed().contains(m)) why = d.name() + " is closed to visitors in " + monthName(m) + ".";
        else if (req.getDays() < d.minDays()) why = d.name() + " needs at least " + days(d.minDays()) + " to do properly.";
        else why = "From " + origin.name() + ", " + d.name() + " is too far to reach and enjoy in " + days(req.getDays()) + ".";
        return res.withRequested(null, why);
    }

    // ------------------------------------------------------------------ evaluation

    /** Internal working object for one destination. */
    static final class Candidate {
        Destination d;
        double km;
        Leg leg;
        String comfort;
        int transport, stay, food, local, activities, buffer, total, nights, rooms;
        String stayLabel;
        Season season;
        CrowdCalc crowd;
        double groundDays;
        double travelRatio;
        int score;
        double vibe = 1;
        List<String> reasons = new ArrayList<>();
        List<String> cautions = new ArrayList<>();
    }

    record Leg(String mode, String label, double hours, boolean overnight, int roundTripCost,
               boolean cabWithYou, int vehicles, String note) {}

    record Season(double stayFactor, double fareFactor, String label) {}

    record CrowdCalc(String level, List<String> reasons) {}

    List<Candidate> evaluateAll(Origin origin, PlanRequest req) {
        List<Candidate> out = new ArrayList<>();
        for (Destination d : req.pool() != null ? req.pool() : catalog.destinations()) {
            Candidate c = evaluate(d, origin, req);
            if (c != null) out.add(c);
        }
        return out;
    }

    Candidate evaluate(Destination d, Origin origin, PlanRequest req) {
        int month = req.start().getMonthValue();
        double km = Catalog.haversineKm(origin.lat(), origin.lon(), d.lat(), d.lon());
        if (km < 40) return null;                          // that's home, not a trip
        if (d.closed().contains(month)) return null;       // e.g. Kedarnath in winter
        if (req.getDays() < d.minDays()) return null;      // e.g. Leh needs acclimatisation days

        String forced = "auto".equals(req.getComfort()) ? null : req.getComfort();
        Candidate best = null;
        if (forced != null) {
            best = cost(d, origin, req, km, forced);
        } else {
            // Highest comfort that leaves ~3% headroom, else highest that fits, else budget tier.
            Candidate fitting = null;
            for (int i = COMFORTS.length - 1; i >= 0; i--) {
                Candidate c = cost(d, origin, req, km, COMFORTS[i]);
                if (c == null) continue;
                if (c.total <= req.getBudget() * 0.97) { best = c; break; }
                if (fitting == null && c.total <= req.getBudget()) fitting = c;
            }
            if (best == null) best = fitting;
            if (best == null) best = cost(d, origin, req, km, "budget");
        }
        if (best == null) return null;
        score(best, req);
        return best;
    }

    /** Full costing at one comfort level. Returns null when the trip isn't feasible in the time available. */
    Candidate cost(Destination d, Origin origin, PlanRequest req, double km, String comfort) {
        int month = req.start().getMonthValue();
        int people = req.getTravellers();
        int days = req.getDays();
        Season season = season(d, req.start(), days);
        double hourValue = clamp(req.getBudget() / (double) (people * days) / 12.0, 60, 1500);

        Leg leg = chooseLeg(d, origin, km, comfort, people, days, month, season, hourValue);
        if (leg == null) return null;

        // Time check: overnight legs cost ~2 waking hours, daytime legs cost their duration.
        double lostPerLeg = leg.overnight() ? 2 : Math.min(leg.hours(), 12);
        double wakingHours = days * 12.0;
        double ground = wakingHours - 2 * lostPerLeg;
        if (days == 1 && !leg.overnight() && leg.hours() > 4) return null;
        if (ground < Math.max(5, wakingHours * 0.45)) return null;

        Candidate c = new Candidate();
        c.d = d; c.km = km; c.leg = leg; c.comfort = comfort; c.season = season;
        c.groundDays = ground / 12.0;
        c.travelRatio = (2 * lostPerLeg) / wakingHours;
        double ci = d.costIndex();
        int groundDays = Math.max(1, (int) Math.round(c.groundDays));

        // Stay
        c.nights = Math.max(0, days - 1);
        c.rooms = rooms(req.getGroup(), people);
        double roomRate;
        if ("budget".equals(comfort)) {
            boolean dorm = "solo".equals(req.getGroup()) || ("friends".equals(req.getGroup()) && people >= 3);
            roomRate = dorm ? 650 : 1300;
            if (dorm) { c.rooms = people; c.stayLabel = "Hostel dorm beds"; }
            else c.stayLabel = "Budget hotel or guesthouse";
        } else if ("standard".equals(comfort)) {
            roomRate = 2900; c.stayLabel = "3-star hotel or good homestay";
        } else {
            roomRate = 7500; c.stayLabel = "4–5 star hotel or boutique stay";
        }
        c.stay = (int) Math.round(roomRate * ci * season.stayFactor() * c.rooms * c.nights);

        // Food
        double foodPP = switch (comfort) { case "budget" -> 500; case "standard" -> 1000; default -> 2100; };
        c.food = (int) Math.round(foodPP * ci * people * days);

        // Local transport (if a hired cab is with you, it's mostly covered already)
        if (leg.cabWithYou()) {
            c.local = (int) Math.round(leg.vehicles() * groundDays * 50 * (comfort.equals("premium") ? 18 : 13));
        } else if ("budget".equals(comfort)) {
            c.local = (int) Math.round(220 * ci * people * groundDays);
        } else {
            int vehicles = (int) Math.ceil(people / 4.0);
            double perVehicleDay = "standard".equals(comfort) ? 1600 : 2700;
            c.local = (int) Math.round(perVehicleDay * ci * vehicles * groundDays);
        }

        // Activities & entry fees
        double comfortMult = switch (comfort) { case "budget" -> 0.7; case "standard" -> 1.0; default -> 1.5; };
        double paceMult = switch (req.getPace()) { case "easy" -> 0.6; case "packed" -> 1.4; default -> 1.0; };
        double activityDays = Math.max(1, c.groundDays);
        c.activities = (int) Math.round(d.activityPerDay() * comfortMult * paceMult * people * activityDays);

        c.transport = leg.roundTripCost();
        int subtotal = c.transport + c.stay + c.food + c.local + c.activities;
        c.buffer = roundTo((int) Math.round(subtotal * 0.05), 50);
        c.total = roundTo(subtotal + c.buffer, 100);
        return c;
    }

    Leg chooseLeg(Destination d, Origin origin, double km, String comfort, int people, int days,
                  int month, Season season, double hourValue) {
        List<Leg> options = new ArrayList<>();
        int vehicles = (int) Math.ceil(people / 4.0);

        // --- Road
        if (!d.isIsland() && !d.roadClosed().contains(month)) {
            double factor = "mountain".equals(d.terrain()) ? 1.55 : "hills".equals(d.terrain()) ? 1.4 : 1.25;
            double roadKm = km * factor;
            double speed = "mountain".equals(d.terrain()) ? 28 : "hills".equals(d.terrain()) ? 36 : 50;
            double hours = roadKm / speed + 0.5;
            if (hours <= 22) {
                boolean cab = "premium".equals(comfort) || ("standard".equals(comfort) && people >= 3 && roadKm <= 500);
                if (cab) {
                    double rate = "premium".equals(comfort) ? 18 : 13;
                    int cost = (int) Math.round(vehicles * (roadKm * 2 * rate + 450.0 * days));
                    options.add(new Leg("road", vehicles > 1 ? vehicles + " private cabs" : "Private cab",
                            round1(hours), false, cost, true, vehicles,
                            "Driver stays with you for local sightseeing; tolls and parking are usually extra."));
                } else {
                    boolean budget = "budget".equals(comfort);
                    double rate = budget ? 1.4 : 2.3;
                    int perPerson = (int) Math.max(budget ? 120 : 350, roadKm * rate);
                    boolean overnight = hours >= 7 && hours <= 15;
                    String label = budget ? (overnight ? "Overnight state bus" : "State bus")
                            : (overnight ? "Overnight AC sleeper bus" : "AC bus");
                    options.add(new Leg("road", label, round1(hours), overnight, perPerson * people * 2,
                            false, 0, "Book on RedBus or the state transport site; overnight buses fill up on weekends."));
                }
            }
        }

        // --- Train
        if (d.railKm() != null && d.railKm() <= 150 && km >= 120) {
            double railKm = km * 1.3;
            double lastMileH = d.railKm() / 35.0;
            double hours = railKm / 55.0 + 0.75 + lastMileH;
            if (hours <= 40) {
                double perKm, min; String cls;
                switch (comfort) {
                    case "budget" -> { perKm = 0.5; min = 180; cls = "Sleeper class"; }
                    case "standard" -> { perKm = 1.35; min = 450; cls = "3AC"; }
                    default -> { perKm = 2.0; min = 800; cls = "2AC"; }
                }
                int fare = (int) Math.max(min, railKm * perKm);
                int lastMile;
                if (d.railKm() <= 3) lastMile = "budget".equals(comfort) ? 60 * people : 300 * vehicles;
                else if ("budget".equals(comfort)) lastMile = (int) Math.max(40, d.railKm() * 2.2) * people;
                else lastMile = (int) Math.max(350, d.railKm() * 15) * vehicles;
                boolean overnight = (railKm / 55.0) >= 7 && (railKm / 55.0) <= 16;
                String label = (overnight ? "Overnight train" : "Train") + " (" + cls + ")";
                String note = d.railKm() > 3
                        ? "Nearest station is " + d.railhead() + ", about " + d.railKm() + " km away; then a taxi or bus. Book on IRCTC 2–8 weeks ahead."
                        : "Arrive at " + d.railhead() + ". Book on IRCTC 2–8 weeks ahead; Tatkal opens a day before.";
                options.add(new Leg("train", label, round1(hours), overnight, (fare * people + lastMile) * 2,
                        false, 0, note));
            }
        }

        // --- Flight
        double minFlightKm = "premium".equals(comfort) ? 300 : 420;
        Origin hub = origin.airport() ? origin : catalog.nearestAirportHub(origin.lat(), origin.lon()).orElse(null);
        double toHubKm = hub == null || hub == origin ? 0 : Catalog.haversineKm(origin.lat(), origin.lon(), hub.lat(), hub.lon()) * 1.25;
        if (hub != null && toHubKm <= 250 && d.airportKm() != null && d.airportKm() <= 300 && km >= minFlightKm) {
            double fare = 2300 + 3.0 * km;
            if ("premium".equals(comfort)) fare *= 1.5;
            fare *= season.fareFactor();
            if (d.airportMinor()) fare *= 1.3;
            boolean connecting = km > 1500 || d.airportMinor();
            double hours = 2.5 + km / 650.0 + (connecting ? 2.5 : 0) + d.airportKm() / 40.0 + toHubKm / 50.0;
            int lastMile = "budget".equals(comfort) && d.airportKm() > 40
                    ? (int) (d.airportKm() * 3) * people
                    : (int) Math.max(450, d.airportKm() * 15) * vehicles;
            if (toHubKm > 0) lastMile += "budget".equals(comfort) ? (int) (toHubKm * 1.5) * people : (int) (toHubKm * 14) * vehicles;
            String viaHub = toHubKm > 0 ? "Get to " + hub.name() + " airport (about " + Math.round(toHubKm) + " km), then f" : "F";
            String note = viaHub + (d.airportKm() > 25
                    ? "ly to " + d.airport() + ", about " + d.airportKm() + " km from " + d.name() + ", then a taxi. Fares rise sharply inside two weeks."
                    : "ly to " + d.airport() + ". Fares rise sharply inside two weeks of travel.");
            options.add(new Leg("flight", connecting ? "Flight (likely one stop)" : "Flight", round1(hours), false,
                    (int) Math.round(fare * people + lastMile) * 2, false, 0, note));
        }

        if (options.isEmpty()) return null;
        // Generalised cost: money + the value of daylight hours spent travelling.
        return options.stream().min(Comparator.comparingDouble(l -> {
            double lostHours = l.overnight() ? 2 : l.hours();
            return l.roundTripCost() + 2 * lostHours * hourValue * people;
        })).orElse(null);
    }

    static int rooms(String group, int people) {
        return switch (group) {
            case "solo", "couple" -> 1;
            case "family" -> (int) Math.ceil(people / 3.0);
            default -> (int) Math.ceil(people / 2.0);
        };
    }

    Season season(Destination d, LocalDate start, int days) {
        LocalDate end = start.plusDays(Math.max(0, days - 1));
        if (overlapsYearEnd(start, end)) return new Season(1.45, 1.35, "Christmas–New Year rates (about +45% on stays)");
        int m = start.getMonthValue();
        if (d.peak().contains(m)) return new Season(1.25, 1.2, "Peak season (about +25% on stays)");
        if (d.avoid().contains(m)) return new Season(0.8, 0.95, "Off-season rates (about −20% on stays)");
        return new Season(1.0, 1.0, "Regular season rates");
    }

    static boolean overlapsYearEnd(LocalDate start, LocalDate end) {
        for (LocalDate x = start; !x.isAfter(end); x = x.plusDays(1)) {
            if ((x.getMonth() == Month.DECEMBER && x.getDayOfMonth() >= 22) || (x.getMonth() == Month.JANUARY && x.getDayOfMonth() <= 1)) {
                return true;
            }
        }
        return false;
    }

    CrowdCalc crowd(Destination d, LocalDate start, int days) {
        double s = d.popularity();
        List<String> reasons = new ArrayList<>();
        int m = start.getMonthValue();
        LocalDate end = start.plusDays(Math.max(0, days - 1));
        if (overlapsYearEnd(start, end)) { s += 1.5; reasons.add("Christmas–New Year holidays"); }
        else if (d.peak().contains(m)) { s += 1.2; reasons.add("peak season for " + d.name()); }
        if (d.avoid().contains(m)) { s -= 1.2; reasons.add("off-season, so fewer visitors"); }
        boolean weekend = false;
        for (LocalDate x = start; !x.isAfter(end); x = x.plusDays(1)) {
            if (x.getDayOfWeek() == DayOfWeek.SATURDAY || x.getDayOfWeek() == DayOfWeek.SUNDAY) { weekend = true; break; }
        }
        if (weekend) { s += 0.5; reasons.add("your dates include a weekend"); }
        else reasons.add("weekdays only, so quieter than weekends");
        if (d.popularity() >= 3 && reasons.size() <= 1) reasons.add(0, "one of India's most visited places");
        String level = s <= 2.0 ? "low" : s <= 3.6 ? "moderate" : "high";
        return new CrowdCalc(level, reasons);
    }

    // ------------------------------------------------------------------ scoring

    void score(Candidate c, PlanRequest req) {
        Destination d = c.d;
        int m = req.start().getMonthValue();
        String month = monthName(m);
        int hi = d.highC()[m - 1], lo = d.lowC()[m - 1];
        c.crowd = crowd(d, req.start(), req.getDays());

        // Trip type
        double vibe;
        List<String> vibes = req.getVibes();
        List<String> matched = new ArrayList<>();
        if (vibes.isEmpty()) {
            vibe = 0.55 + 0.05 * d.popularity();
        } else {
            double sum = 0;
            for (String v : vibes) {
                double t = d.tag(v);
                sum += t;
                if (t >= 0.6) matched.add(v);
            }
            vibe = sum / vibes.size();
        }
        c.vibe = vibes.isEmpty() ? 1 : vibe;

        // Season
        double seasonFit = d.best().contains(m) ? 1 : d.avoid().contains(m) ? 0.15 : 0.6;

        // Weather preference against the typical high for that month
        double weather = switch (req.getWeather()) {
            case "cold" -> hi <= 15 ? 1 : hi <= 22 ? 0.65 : hi <= 28 ? 0.25 : 0.05;
            case "mild" -> (hi >= 17 && hi <= 29) ? 1 : (hi >= 12 && hi <= 33) ? 0.55 : 0.15;
            case "warm" -> hi >= 38 ? 0.4 : hi >= 27 ? 1 : hi >= 22 ? 0.55 : 0.15;
            default -> (hi >= 40 || hi <= 2) ? 0.35 : 0.75;
        };

        // Crowds
        double crowd = switch (req.getCrowd()) {
            case "quiet" -> switch (c.crowd.level()) { case "low" -> 1; case "moderate" -> 0.5; default -> 0.1; };
            case "lively" -> switch (c.crowd.level()) { case "high" -> 1; case "moderate" -> 0.75; default -> 0.35; };
            default -> 0.75;
        };

        // Travel effort (worse for an easy pace)
        double effortPenalty = "easy".equals(req.getPace()) ? 2.0 : "packed".equals(req.getPace()) ? 1.1 : 1.5;
        double travel = Math.max(0, 1 - c.travelRatio * effortPenalty);

        // Budget use: within budget, using 50–95% of it is ideal; over budget scores 0
        double used = c.total / (double) req.getBudget();
        double value = used > 1 ? 0 : used > 0.95 ? 0.35 : used >= 0.4 ? 1 : 0.75;

        double raw = 0.36 * vibe + 0.18 * seasonFit + 0.13 * weather + 0.09 * crowd + 0.16 * travel + 0.08 * value;
        double adj = 0;
        String group = req.getGroup();
        if ("family".equals(group) && d.tag("nightlife") >= 0.8) adj -= 0.06;
        if ("family".equals(group) && "mountain".equals(d.terrain()) && d.minDays() >= 4) adj -= 0.05;
        if ("friends".equals(group) && d.tag("nightlife") >= 0.4) adj += 0.03;
        if ("couple".equals(group) && d.tag("relax") >= 0.8) adj += 0.02;
        String kind = d.discoveryKind();
        if ("hidden".equals(req.getDiscovery())) adj += "offbeat".equals(kind) ? 0.07 : "famous".equals(kind) ? -0.04 : 0;
        if ("famous".equals(req.getDiscovery())) adj += "famous".equals(kind) ? 0.04 : "offbeat".equals(kind) ? -0.03 : 0;

        // Too many days for what's there?
        int perDay = "easy".equals(req.getPace()) ? 2 : "packed".equals(req.getPace()) ? 4 : 3;
        double contentDays = d.highlights().size() / (double) perDay + 1.5;
        boolean tooLong = c.groundDays > contentDays + 1.5;
        if (tooLong) adj -= Math.min(0.12, 0.03 * (c.groundDays - contentDays));

        c.score = (int) Math.round(clamp((raw + adj) * 100, 1, 99));

        // ---- Reasons, in order of what people care about
        if (!matched.isEmpty()) c.reasons.add("Strong for " + joinWords(matched.stream().map(TripPlanner::vibePhrase).toList()) + ".");
        if ("offbeat".equals(kind)) c.reasons.add("A hidden gem: far fewer visitors than the usual circuit.");
        if (d.best().contains(m)) c.reasons.add(month + " is one of the best months to visit.");
        String wLabel = weatherLabel(hi, lo, m, d);
        if (!"any".equals(req.getWeather()) && weather >= 0.9) c.reasons.add(wLabel + " in " + month + " (" + lo + "–" + hi + "°C), as you wanted.");
        if ("quiet".equals(req.getCrowd()) && "low".equals(c.crowd.level())) c.reasons.add("Usually quiet on your dates.");
        if (c.leg.overnight()) c.reasons.add(c.leg.label() + " each way, so you don't lose a day to travel.");
        else if (c.leg.hours() <= 5) c.reasons.add("Only about " + hoursText(c.leg.hours()) + " each way by " + modeWord(c.leg) + ".");
        int remaining = req.getBudget() - c.total;
        if (remaining >= req.getBudget() * 0.05) c.reasons.add("Leaves about " + inr(remaining) + " of your budget spare.");

        // ---- Cautions
        if (d.avoid().contains(m)) {
            if (m >= 6 && m <= 9) c.cautions.add(month + " is monsoon season here: expect heavy rain and some closures.");
            else c.cautions.add(month + " isn't a good time for " + d.name() + "; " + (hi >= 36 ? "it's very hot." : hi <= 5 ? "it's bitterly cold." : "conditions are poor."));
        } else if (hi >= 38) c.cautions.add("Daytime highs around " + hi + "°C; plan outdoor sightseeing for mornings and evenings.");
        if (lo <= 2 && !"cold".equals(req.getWeather())) c.cautions.add("Nights drop to around " + lo + "°C; pack warm layers.");
        if ("high".equals(c.crowd.level())) c.cautions.add("Expect crowds and book stays early.");
        if (c.travelRatio > 0.35 && !c.leg.overnight()) c.cautions.add("Travel takes a big share of your time (" + hoursText(c.leg.hours()) + " each way).");
        if (tooLong) c.cautions.add(days(req.getDays()) + " is longer than most people spend in " + d.name() + "; consider adding a nearby stop.");
        d.cautionsFor(m).stream().limit(2).forEach(c.cautions::add);
    }

    List<Candidate> pickDiverse(List<Candidate> pool, int n) {
        List<Candidate> remaining = new ArrayList<>(pool);
        List<Candidate> chosen = new ArrayList<>();
        while (chosen.size() < n && !remaining.isEmpty()) {
            Candidate best = null;
            double bestVal = -1e9;
            for (Candidate c : remaining) {
                long sameState = chosen.stream().filter(x -> x.d.state().equals(c.d.state())).count();
                double val = c.score - 6 * sameState;
                if (val > bestVal || (val == bestVal && best != null && c.total < best.total)) { bestVal = val; best = c; }
            }
            chosen.add(best);
            remaining.remove(best);
        }
        return chosen;
    }

    /** For a mixed search, swap the weakest pick for the best hidden gem if one scores within reach. */
    List<Candidate> ensureHiddenGem(List<Candidate> top, List<Candidate> pool, PlanRequest req) {
        if (!"mix".equals(req.getDiscovery()) || top.size() < 3) return top;
        if (top.stream().anyMatch(c -> "offbeat".equals(c.d.discoveryKind()))) return top;
        Candidate gem = pool.stream().filter(c -> !top.contains(c) && "offbeat".equals(c.d.discoveryKind()))
                .max(Comparator.comparingInt(c -> c.score)).orElse(null);
        if (gem == null || gem.score < top.get(0).score - 15) return top;
        List<Candidate> out = new ArrayList<>(top.subList(0, top.size() - 1));
        out.add(gem);
        return out;
    }

    // ------------------------------------------------------------------ suggestions

    List<Suggestion> suggestionsWhenOver(Origin origin, PlanRequest req, Candidate bestMatch, Candidate cheapest) {
        List<Suggestion> out = new ArrayList<>();
        int forBest = roundUp(bestMatch.total, 500);
        out.add(new Suggestion("Raise the budget to " + inr(forBest) + " for " + bestMatch.d.name(), Map.of("budget", forBest)));
        if (cheapest != bestMatch) {
            int forCheapest = roundUp(cheapest.total, 500);
            if (forCheapest < forBest) {
                out.add(new Suggestion("Raise the budget to " + inr(forCheapest) + " (cheapest option: " + cheapest.d.name() + ")", Map.of("budget", forCheapest)));
            }
        }
        for (int nd = req.getDays() - 1; nd >= 1; nd--) {
            PlanRequest alt = req.with(null, nd, null);
            boolean any = evaluateAll(origin, alt).stream().anyMatch(c -> c.total <= req.getBudget());
            if (any) { out.add(new Suggestion("Plan " + days(nd) + " instead of " + req.getDays(), Map.of("days", nd))); break; }
        }
        if ("standard".equals(req.getComfort()) || "premium".equals(req.getComfort())) {
            PlanRequest alt = req.with(null, null, "budget");
            boolean any = evaluateAll(origin, alt).stream().anyMatch(c -> c.total <= req.getBudget());
            if (any) out.add(new Suggestion("Switch to budget stays and transport", Map.of("comfort", "budget")));
        }
        return out;
    }

    List<Suggestion> suggestionsWhenNoMatch(Origin origin, PlanRequest req) {
        List<Suggestion> out = new ArrayList<>();
        for (int nd = req.getDays() + 1; nd <= Math.min(PlanRequest.MAX_DAYS, req.getDays() + 5); nd++) {
            if (!evaluateAll(origin, req.with(null, nd, null)).isEmpty()) {
                out.add(new Suggestion("Plan " + days(nd) + " so farther places become reachable", Map.of("days", nd)));
                break;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ output mapping

    Trip toTrip(Candidate c, Origin origin, PlanRequest req) {
        Destination d = c.d;
        int m = req.start().getMonthValue();
        int hi = d.highC()[m - 1], lo = d.lowC()[m - 1];
        int remaining = req.getBudget() - c.total;
        String fit = remaining < 0 ? "over" : remaining < req.getBudget() * 0.08 ? "tight" : "fits";

        Cost cost = new Cost(c.transport, c.stay, c.food, c.local, c.activities, c.buffer, c.total,
                (int) Math.round(c.total / (double) req.getTravellers()), c.comfort, c.nights, c.rooms, c.stayLabel,
                c.season.label());
        Transport t = new Transport(c.leg.mode(), c.leg.label(), c.leg.hours(), c.leg.overnight(), c.leg.note());
        List<String> tags = d.tags().entrySet().stream()
                .filter(e -> e.getValue() >= 0.6)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        List<String> best = d.best().stream().sorted().map(TripPlanner::shortMonth).toList();

        return new Trip(d.id(), d.name(), d.state(), d.image(), d.imageAlt(), d.summary(), tags, c.score,
                c.reasons, c.cautions.stream().distinct().limit(4).toList(), (int) Math.round(c.km), t, cost,
                new BudgetFit(req.getBudget(), Math.max(0, remaining), Math.max(0, -remaining), fit),
                new Weather(monthName(m), lo, hi, weatherLabel(hi, lo, m, d), "climate"),
                new Crowd(c.crowd.level(), c.crowd.reasons()), best, d.highlights(),
                dayPlan(c, origin, req), bookingLinks(c, origin, req), d.whyItFits(),
                d.discoveryKind(), d.localTip(), d.isAi() ? "ai" : "catalog", d.lat(), d.lon());
    }

    List<Day> dayPlan(Candidate c, Origin origin, PlanRequest req) {
        Destination d = c.d;
        int days = req.getDays();
        int perDay = "easy".equals(req.getPace()) ? 2 : "packed".equals(req.getPace()) ? 4 : 3;
        List<String> pool = new ArrayList<>(d.highlights());
        Leg leg = c.leg;
        String hours = hoursText(leg.hours());
        List<Day> out = new ArrayList<>();
        boolean acclimatise = "leh".equals(d.id());

        if (days == 1) {
            List<String> items = new ArrayList<>();
            items.add(leg.overnight() ? "Arrive early on the " + lower(leg.label()) + " from " + origin.name()
                    : "Leave " + origin.name() + " early: about " + hours + " by " + modeWord(leg));
            items.addAll(take(pool, Math.max(1, perDay - 1)));
            items.add(leg.overnight() ? "Board the " + lower(leg.label()) + " home in the evening" : "Head back in the evening");
            out.add(new Day(1, "Day trip to " + d.name(), items));
            return out;
        }

        for (int day = 1; day <= days; day++) {
            List<String> items = new ArrayList<>();
            String title;
            if (day == 1) {
                if (leg.overnight()) {
                    items.add("Arrive in the morning on the " + lower(leg.label()) + " from " + origin.name() + " (leave the night before)");
                    if (acclimatise) items.add("Rest; no exertion on the first day at altitude");
                    else items.addAll(take(pool, perDay - 1));
                    title = "Arrive and explore";
                } else {
                    items.add("Travel from " + origin.name() + ": about " + hours + " by " + modeWord(leg));
                    items.add("Check in: " + lower(c.stayLabel));
                    if (acclimatise) items.add("Rest for the day to acclimatise; drink plenty of water");
                    else if (leg.hours() <= 6) items.addAll(take(pool, 1));
                    title = "Travel and settle in";
                }
            } else if (day == days) {
                items.addAll(take(pool, leg.hours() > 6 && !leg.overnight() ? 0 : 1));
                items.add(leg.overnight() ? "Evening " + lower(leg.label()) + " back to " + origin.name()
                        : "Head back to " + origin.name() + " (about " + hours + ")");
                title = "Last morning and home";
            } else {
                List<String> picks = take(pool, perDay);
                if (picks.isEmpty()) picks = List.of("Free day: revisit a favourite spot, try a cooking class, or rest");
                items.addAll(picks);
                boolean freeDay = picks.get(0).startsWith("Free day");
                title = freeDay ? "At your own pace" : day == 2 ? "Explore " + d.name() : "More of " + d.name();
            }
            if (items.isEmpty()) continue;
            out.add(new Day(day, title, items));
        }
        return out;
    }

    Map<String, String> bookingLinks(Candidate c, Origin origin, PlanRequest req) {
        Map<String, String> links = new LinkedHashMap<>();
        LocalDate in = req.start();
        LocalDate out = in.plusDays(Math.max(1, req.getDays() - 1));
        if (c.nights > 0) {
            links.put("stay", "https://www.booking.com/searchresults.html?ss=" + enc(c.d.name() + ", " + c.d.state())
                    + "&checkin=" + in + "&checkout=" + out + "&group_adults=" + req.getTravellers()
                    + "&no_rooms=" + Math.min(c.rooms, 30) + "&selected_currency=INR");
        }
        switch (c.leg.mode()) {
            case "flight" -> links.put("transport", "https://www.google.com/travel/flights?q=" + enc("Flights from " + origin.name() + " to " + c.d.airport() + " on " + in));
            case "train" -> links.put("transport", "https://www.irctc.co.in/nget/train-search");
            default -> links.put("transport", c.leg.cabWithYou()
                    ? "https://www.google.com/maps/dir/" + enc(origin.name()) + "/" + enc(c.d.name() + ", " + c.d.state())
                    : "https://www.redbus.in/");
        }
        links.put("map", "https://www.google.com/maps/search/?api=1&query=" + enc(c.d.name() + ", " + c.d.state()));
        return links;
    }

    Map<String, Object> queryEcho(PlanRequest req, Origin origin) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("origin", req.getOrigin());
        q.put("originLabel", origin != null ? origin.name() : req.getOrigin());
        q.put("originKnown", origin != null);
        q.put("startDate", req.getStartDate());
        q.put("dateAssumed", req.dateAssumed());
        q.put("month", monthName(req.start().getMonthValue()));
        q.put("days", req.getDays());
        q.put("travellers", req.getTravellers());
        q.put("group", req.getGroup());
        q.put("budget", req.getBudget());
        q.put("vibes", req.getVibes());
        q.put("weather", req.getWeather());
        q.put("crowd", req.getCrowd());
        q.put("pace", req.getPace());
        q.put("comfort", req.getComfort());
        return q;
    }

    // ------------------------------------------------------------------ text helpers

    static String weatherLabel(int hi, int lo, int month, Destination d) {
        String base;
        if (hi <= 4) base = "Freezing";
        else if (hi <= 12) base = "Cold";
        else if (hi <= 20) base = lo <= 6 ? "Cool days, cold nights" : "Cool";
        else if (hi <= 28) base = "Pleasant";
        else if (hi <= 33) base = "Warm";
        else if (hi <= 38) base = "Hot";
        else base = "Very hot";
        boolean wet = month >= 6 && month <= 9 && d.avoid().contains(month) && !"desert".equals(d.terrain());
        return wet ? base + " and rainy" : base;
    }

    static String vibePhrase(String v) {
        return switch (v) {
            case "relax" -> "slowing down";
            case "adventure" -> "adventure";
            case "spiritual" -> "temples and spiritual sites";
            case "nightlife" -> "nightlife";
            case "heritage" -> "history and architecture";
            case "nature" -> "nature";
            case "beach" -> "beaches";
            case "hills" -> "the hills";
            case "food" -> "food";
            case "wildlife" -> "wildlife";
            default -> v;
        };
    }

    static String modeWord(Leg l) {
        return switch (l.mode()) {
            case "flight" -> "air";
            case "train" -> "train";
            default -> l.cabWithYou() ? "cab" : "bus";
        };
    }

    static String hoursText(double h) {
        if (h < 1) return "under an hour";
        long r = Math.round(h * 2);
        double half = r / 2.0;
        return (half == Math.floor(half) ? String.valueOf((long) half) : String.valueOf(half)) + " hours";
    }

    static List<String> take(List<String> pool, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n && !pool.isEmpty(); i++) out.add(pool.remove(0));
        return out;
    }

    static String joinWords(List<String> words) {
        if (words.size() <= 1) return words.isEmpty() ? "" : words.get(0);
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
    }

    static String monthName(int m) { return Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH); }
    static String shortMonth(int m) { return Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH); }
    static String lower(String s) { return s == null || s.isEmpty() ? "" : Character.toLowerCase(s.charAt(0)) + s.substring(1); }
    static String people(int n) { return n == 1 ? "1 traveller" : n + " travellers"; }
    static String days(int n) { return n == 1 ? "1 day" : n + " days"; }
    static String inr(int amount) {
        return "₹" + java.text.NumberFormat.getIntegerInstance(new Locale("en", "IN")).format(amount);
    }
    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static double round1(double v) { return Math.round(v * 10) / 10.0; }
    static int roundTo(int v, int step) { return (int) (Math.round(v / (double) step) * step); }
    static int roundUp(int v, int step) { return ((v + step - 1) / step) * step; }
}
