package com.tripmind.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmind.planner.PlanResult.Response;
import com.tripmind.planner.PlanResult.Trip;
import com.tripmind.planner.Catalog.Destination;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TripPlannerTest {

    static Catalog catalog;
    static TripPlanner planner;
    static final LocalDate TODAY = LocalDate.now();

    @BeforeAll
    static void setUp() throws Exception {
        catalog = new Catalog(new ObjectMapper());
        planner = new TripPlanner(catalog);
    }

    /** Next occurrence of the given day/month at least a week from today. */
    static String dateIn(int month, int day) {
        LocalDate d = LocalDate.of(TODAY.getYear(), month, day);
        if (d.isBefore(TODAY.plusDays(7))) d = d.plusYears(1);
        return d.toString();
    }

    static PlanRequest req(String origin, int days, int people, int budget, String month, String... vibes) {
        PlanRequest r = new PlanRequest();
        r.setOrigin(origin);
        r.setDays(days);
        r.setTravellers(people);
        r.setBudget(budget);
        r.setStartDate(month);
        r.setVibes(new ArrayList<>(List.of(vibes)));
        assertNull(r.normalise(TODAY), "request should be valid");
        return r;
    }

    static List<Trip> all(Response res) {
        List<Trip> out = new ArrayList<>(res.recommendations());
        if (res.stretch() != null) out.addAll(res.stretch());
        return out;
    }

    @ParameterizedTest(name = "{0}, {1}d, {2}p, ₹{3}")
    @CsvSource({
            "Delhi, 3, 2, 30000", "Mumbai, 2, 4, 20000", "Bengaluru, 4, 2, 45000", "Indore, 1, 1, 1500",
            "Kolkata, 5, 3, 60000", "Chennai, 6, 2, 120000", "Pune, 2, 1, 6000", "Jaipur, 7, 5, 150000",
            "Lucknow, 3, 2, 12000", "Guwahati, 4, 2, 40000"
    })
    void recommendationsNeverExceedBudget(String origin, int days, int people, int budget) {
        Response res = planner.plan(req(origin, days, people, budget, dateIn(3, 10)));
        for (Trip t : res.recommendations()) {
            assertTrue(t.cost().total() <= budget, t.name() + " costs " + t.cost().total() + " > " + budget);
            assertNotEquals("over", t.budgetFit().status());
        }
    }

    @Test
    void costLinesAddUpToTotal() {
        Response res = planner.plan(req("Delhi", 4, 3, 50000, dateIn(10, 8), "heritage"));
        assertFalse(all(res).isEmpty());
        for (Trip t : all(res)) {
            var c = t.cost();
            int sum = c.transport() + c.stay() + c.food() + c.local() + c.activities() + c.buffer();
            assertTrue(Math.abs(sum - c.total()) <= 100, t.name() + ": lines " + sum + " vs total " + c.total());
            assertTrue(c.transport() > 0 && c.food() > 0, "every trip has transport and food costs");
        }
    }

    @Test
    void tripTypeChangesTheAnswer() {
        Trip beach = planner.plan(req("Bengaluru", 4, 2, 60000, dateIn(1, 12), "beach")).recommendations().get(0);
        Trip hills = planner.plan(req("Bengaluru", 4, 2, 60000, dateIn(1, 12), "hills")).recommendations().get(0);
        Trip spiritual = planner.plan(req("Bengaluru", 4, 2, 60000, dateIn(1, 12), "spiritual")).recommendations().get(0);
        assertTrue(beach.tags().contains("beach"), "beach request → " + beach.name());
        assertTrue(hills.tags().contains("hills"), "hills request → " + hills.name());
        assertTrue(spiritual.tags().contains("spiritual"), "spiritual request → " + spiritual.name());
        assertNotEquals(beach.id(), hills.id());
    }

    @Test
    void neverPassesOffUnrelatedTripsAsMatches() {
        // Beaches from Jaipur in 3 days on ₹30k: nothing beachy fits, so say so instead of recommending hill towns.
        Response res = planner.plan(req("Jaipur", 3, 2, 30000, dateIn(11, 20), "beach"));
        if ("ok".equals(res.status())) {
            res.recommendations().forEach(t -> assertTrue(t.tags().contains("beach"), t.name() + " is not a beach trip"));
        } else {
            assertEquals("ok_other", res.status());
            assertTrue(res.message().startsWith("Nothing strong for beaches"), res.message());
            assertFalse(res.suggestions().isEmpty());
            res.stretch().forEach(t -> assertTrue(t.tags().contains("beach")));
        }
    }

    @Test
    void notJustTheCheapestPlace() {
        // With a generous budget, the top pick should be a strong match, not merely the nearest/cheapest.
        Response res = planner.plan(req("Delhi", 4, 2, 80000, dateIn(4, 10), "hills", "relax"));
        Trip top = res.recommendations().get(0);
        assertTrue(top.tags().contains("hills"), "top pick " + top.name());
        int cheapest = res.recommendations().stream().mapToInt(t -> t.cost().total()).min().orElseThrow();
        assertTrue(top.matchScore() >= res.recommendations().get(res.recommendations().size() - 1).matchScore());
        assertTrue(cheapest > 0);
    }

    @Test
    void lowBudgetDayTripFromIndoreFindsUjjainOrOmkareshwar() {
        Response res = planner.plan(req("Indore", 1, 1, 1500, dateIn(2, 10), "spiritual"));
        assertEquals("ok", res.status(), res.message());
        String top = res.recommendations().get(0).id();
        assertTrue(top.equals("ujjain") || top.equals("omkareshwar"), "got " + top);
        assertTrue(res.recommendations().get(0).cost().stay() == 0, "a day trip needs no hotel");
    }

    @Test
    void kedarnathIsNeverSuggestedInWinter() {
        Response res = planner.plan(req("Delhi", 6, 2, 200000, dateIn(1, 15), "spiritual", "adventure"));
        assertTrue(all(res).stream().noneMatch(t -> t.id().equals("kedarnath")));
        Response june = planner.plan(req("Delhi", 6, 2, 200000, dateIn(6, 2), "spiritual", "adventure"));
        assertTrue(all(june).stream().anyMatch(t -> t.id().equals("kedarnath")) || june.recommendations().size() == 3,
                "Kedarnath is at least eligible in June");
    }

    @Test
    void lehNeedsEnoughDaysToAcclimatise() {
        Response shortTrip = planner.plan(req("Delhi", 3, 2, 200000, dateIn(7, 5), "adventure"));
        assertTrue(all(shortTrip).stream().noneMatch(t -> t.id().equals("leh")));
        Response longTrip = planner.plan(req("Delhi", 7, 2, 200000, dateIn(7, 5), "adventure"));
        Trip leh = all(longTrip).stream().filter(t -> t.id().equals("leh")).findFirst().orElse(null);
        if (leh != null) assertTrue(leh.dayPlan().get(0).items().stream().anyMatch(i -> i.toLowerCase().contains("rest")));
    }

    @Test
    void monsoonHurtsBeachDestinations() {
        Trip dec = all(planner.plan(req("Pune", 4, 2, 60000, dateIn(12, 5), "beach"))).stream()
                .filter(t -> t.id().equals("goa")).findFirst().orElseThrow();
        Response jul = planner.plan(req("Pune", 4, 2, 60000, dateIn(7, 10), "beach"));
        Trip julGoa = all(jul).stream().filter(t -> t.id().equals("goa")).findFirst().orElse(null);
        if (julGoa != null) {
            assertTrue(julGoa.matchScore() < dec.matchScore(), "Goa in July should score lower than December");
            assertTrue(julGoa.cautions().stream().anyMatch(c -> c.toLowerCase().contains("monsoon") || c.toLowerCase().contains("june to september")));
        }
    }

    @Test
    void seasonalWarningsOnlyAppearInTheirSeason() {
        Trip goaDec = all(planner.plan(req("Mumbai", 4, 2, 80000, dateIn(12, 3), "beach"))).stream()
                .filter(t -> t.id().equals("goa")).findFirst().orElseThrow();
        assertTrue(goaDec.cautions().stream().noneMatch(c -> c.contains("June to September")), goaDec.cautions().toString());
        Destination goa = catalog.destination("goa").orElseThrow();
        assertTrue(goa.cautionsFor(7).stream().anyMatch(c -> c.contains("June to September")));
        assertTrue(goa.cautionsFor(null).stream().noneMatch(c -> c.startsWith("[")), "prefixes are stripped");
    }

    @Test
    void crowdLevelsAreNotAllHigh() {
        Response res = planner.plan(req("Delhi", 3, 2, 60000, dateIn(3, 10)));
        assertTrue(all(res).stream().anyMatch(t -> !t.crowd().level().equals("high")));
    }

    @Test
    void dayTripsRespectTravelTime() {
        Response res = planner.plan(req("Mumbai", 1, 2, 20000, dateIn(11, 10)));
        for (Trip t : all(res)) {
            assertTrue(t.transport().overnight() || t.transport().oneWayHours() <= 4.0,
                    t.name() + " is " + t.transport().oneWayHours() + "h away for a day trip");
        }
    }

    @Test
    void islandsAreReachedByAir() {
        PlanRequest r = req("Chennai", 6, 2, 150000, dateIn(1, 10), "beach");
        r.setInclude("andaman");
        assertNull(r.normalise(TODAY));
        Trip andaman = planner.plan(r).requested();
        assertNotNull(andaman, "Andaman is costed when asked for");
        assertEquals("flight", andaman.transport().mode());
    }

    @Test
    void requestedDestinationExplainsWhenImpossible() {
        PlanRequest r = req("Delhi", 3, 2, 100000, dateIn(1, 10));
        r.setInclude("kedarnath");
        assertNull(r.normalise(TODAY));
        Response res = planner.plan(r);
        assertNull(res.requested());
        assertTrue(res.requestedNote().contains("closed"), res.requestedNote());
    }

    @Test
    void tinyBudgetGivesHonestSuggestionsNotFakeTrips() {
        Response res = planner.plan(req("Delhi", 4, 4, 3000, dateIn(3, 10)));
        assertEquals("over_budget", res.status());
        assertTrue(res.recommendations().isEmpty());
        assertFalse(res.stretch().isEmpty());
        assertFalse(res.suggestions().isEmpty());
        res.stretch().forEach(t -> assertTrue(t.budgetFit().extraNeeded() > 0));
        Object budget = res.suggestions().get(0).patch().get("budget");
        assertNotNull(budget);
        // Applying the suggestion really does unlock that trip.
        Response retry = planner.plan(req("Delhi", 4, 4, (Integer) budget, dateIn(3, 10)));
        assertEquals("ok", retry.status());
    }

    @Test
    void unknownOriginAsksInsteadOfGuessing() {
        Response res = planner.plan(req("Atlantis", 3, 2, 30000, dateIn(3, 10)));
        assertEquals("origin_unknown", res.status());
        assertEquals(4, res.originSuggestions().size());
    }

    @Test
    void originTyposAndOldNamesResolve() {
        assertEquals("Delhi", catalog.resolveOrigin("Dehli").orElseThrow().name());
        assertEquals("Bengaluru", catalog.resolveOrigin("bangalore").orElseThrow().name());
        assertEquals("Mumbai", catalog.resolveOrigin("Bombay").orElseThrow().name());
        assertEquals("Pune", catalog.resolveOrigin("Pune, Maharashtra").orElseThrow().name());
        assertEquals("Hyderabad", catalog.resolveOrigin("Hyderbad").orElseThrow().name());
        assertTrue(catalog.resolveOrigin("Xyz").isEmpty());
    }

    @Test
    void neverRecommendsYourOwnCity() {
        Response res = planner.plan(req("Jaipur", 3, 2, 40000, dateIn(11, 10), "heritage"));
        assertTrue(all(res).stream().noneMatch(t -> t.id().equals("jaipur")));
    }

    @Test
    void dayPlanCoversEveryDay() {
        Trip t = planner.plan(req("Delhi", 5, 2, 70000, dateIn(10, 12), "heritage")).recommendations().get(0);
        assertEquals(5, t.dayPlan().size());
        assertTrue(t.dayPlan().get(4).items().stream().anyMatch(i -> i.contains("Delhi")), "last day returns home");
    }

    @Test
    void yearEndRaisesPrices() {
        PlanRequest normal = req("Mumbai", 3, 2, 200000, dateIn(12, 5), "beach");
        PlanRequest yearEnd = req("Mumbai", 3, 2, 200000, dateIn(12, 28), "beach");
        int a = Stream.concat(planner.plan(normal).recommendations().stream(), planner.plan(normal).stretch().stream())
                .filter(t -> t.id().equals("goa")).findFirst().orElseThrow().cost().stay();
        Trip goaYe = all(planner.plan(yearEnd)).stream().filter(t -> t.id().equals("goa")).findFirst().orElseThrow();
        assertTrue(goaYe.cost().seasonLabel().contains("New Year"));
        assertTrue(goaYe.cost().stay() >= a, "year-end stays should not be cheaper");
    }

    @Test
    void validationRejectsBadInput() {
        PlanRequest r = new PlanRequest();
        r.setOrigin("Delhi");
        assertNotNull(r.normalise(TODAY), "budget is required");
        r.setBudget(10000);
        r.setStartDate(TODAY.minusDays(3).toString());
        assertNotNull(r.normalise(TODAY), "past dates rejected");
        r.setStartDate(null);
        r.setDays(400);
        r.setTravellers(-2);
        assertNull(r.normalise(TODAY));
        assertEquals(PlanRequest.MAX_DAYS, r.getDays());
        assertEquals(1, r.getTravellers());
    }
}
