package com.tripmind.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintDecisionEngineTest {

```
@Test
void lowBudgetSpiritualTrip_shouldReturnValidAffordablePlan() {
    DistanceService distanceService = new DistanceService();
    ConstraintDecisionEngine engine = new ConstraintDecisionEngine(distanceService);

    ConstraintDecisionEngine.EngineResult result = engine.decide(
            "Indore",
            1000,
            1,
            "Spiritual",
            1
    );

    assertNotNull(result, "EngineResult should not be null");

    ConstraintDecisionEngine.Decision d = result.decision();
    assertNotNull(d, "Decision should not be null for valid low-budget trip");

    assertNotNull(d.destinationKey());
    assertNotNull(d.destinationDisplay());

    assertNotNull(d.costBreakdown());
    int total = ((Number) d.costBreakdown().get("total")).intValue();
    int travel = ((Number) d.costBreakdown().get("travel")).intValue();

    // Ensure trip fits within budget
    assertTrue(total <= 1000, "Total cost should be within budget");

    // Travel should not exceed 40–50% for low budget
    assertTrue(travel <= 500, "Travel cost should be reasonable for low budget");

    // Ensure no stay cost for 1-day trip
    int stay = ((Number) d.costBreakdown().get("stay")).intValue();
    assertEquals(0, stay, "1-day trip should not include stay cost");
}

@Test
void veryLowBudget_shouldReturnFailureWithRequiredBudget() {
    DistanceService distanceService = new DistanceService();
    ConstraintDecisionEngine engine = new ConstraintDecisionEngine(distanceService);

    ConstraintDecisionEngine.EngineResult result = engine.decide(
            "Delhi",
            500,
            1,
            "Adventure",
            1
    );

    // In extreme low budget, decision may be null but required budget must exist
    assertNotNull(result, "EngineResult should not be null");

    if (result.decision() == null) {
        assertNotNull(result.minRequiredBudget(), "Minimum required budget should be provided");
        assertTrue(result.minRequiredBudget() > 500, "Required budget should be higher than current");
    } else {
        // If fallback plan exists, ensure it's within budget
        int total = ((Number) result.decision().costBreakdown().get("total")).intValue();
        assertTrue(total <= 500);
    }
}

@Test
void highBudget_shouldAlwaysReturnValidPlan() {
    DistanceService distanceService = new DistanceService();
    ConstraintDecisionEngine engine = new ConstraintDecisionEngine(distanceService);

    ConstraintDecisionEngine.EngineResult result = engine.decide(
            "Mumbai",
            200000,
            5,
            "Luxury",
            2
    );

    assertNotNull(result);
    assertNotNull(result.decision(), "High budget should always produce a valid plan");

    int total = ((Number) result.decision().costBreakdown().get("total")).intValue();

    // Allow slight tolerance (10%)
    assertTrue(total <= 200000 * 1.1, "Total cost should be within acceptable tolerance");
}

@Test
void shouldRespectToleranceRange() {
    DistanceService distanceService = new DistanceService();
    ConstraintDecisionEngine engine = new ConstraintDecisionEngine(distanceService);

    ConstraintDecisionEngine.EngineResult result = engine.decide(
            "Bangalore",
            10000,
            2,
            "Leisure",
            1
    );

    assertNotNull(result);

    if (result.decision() != null) {
        int total = ((Number) result.decision().costBreakdown().get("total")).intValue();

        // Accept within 10% tolerance
        assertTrue(total <= 11000, "Should allow slight tolerance in cost");
    } else {
        assertNotNull(result.minRequiredBudget(), "Failure must provide required budget");
    }
}

@Test
void failureShouldProvideMinimumRequirements() {
    DistanceService distanceService = new DistanceService();
    ConstraintDecisionEngine engine = new ConstraintDecisionEngine(distanceService);

    ConstraintDecisionEngine.EngineResult result = engine.decide(
            "Chennai",
            800,
            1,
            "Adventure",
            1
    );

    assertNotNull(result);

    if (result.decision() == null) {
        assertNotNull(result.minRequiredBudget(), "Should return minimum required budget");
        assertTrue(result.minRequiredBudget() > 800);

        // Optional: check days recommendation if implemented
        if (result.recommendedDays() != null) {
            assertTrue(result.recommendedDays() >= 1);
        }
    }
}
```

}
