# Algorithms and data structures

The techniques behind the planner, where they live, and why they were chosen. Figures are design parameters or measured timings, not marketing claims.

## Geography

| Technique | Where | Purpose |
|---|---|---|
| **Haversine great-circle distance** | `Catalog.haversineKm` | Distance between two coordinates. Turned into travel distance with terrain factors: road ×1.25 (plains), ×1.4 (hills), ×1.55 (mountains); rail ×1.3. |
| **Nearest-neighbour search** (linear scan, ~70 cities) | `Catalog.nearestAirportHub` | Nearest city with an airport for travellers starting from a town without one |
| **Bounding-box check** | `AiPlaceService.inIndia` | Rejects AI coordinates outside India (6–37.5°N, 68–97.5°E) |

## Matching text

| Technique | Where | Purpose |
|---|---|---|
| **Unicode normalisation + key folding** | `Catalog.key` | "Pālampur", "PALAMPUR " and "palampur" all match |
| **Hash-map alias index** | `Catalog.originIndex` | O(1) lookup of cities, old names (Bombay, Calcutta) and common typos |
| **Levenshtein edit distance**, with a uniqueness check | `Catalog.levenshtein`, `resolveOrigin` | Accepts a typo only if exactly one city is within 1–2 edits; ambiguous input is asked about, not guessed |
| **Ranked suggestions** | `Catalog.suggestOrigins` | Closest 4 cities by edit distance for "did you mean" |

## Choosing transport

For each mode that is possible (road, train, flight), the planner computes a round-trip money cost and a time cost:

```
generalised cost = round-trip fare + 2 × (daylight hours lost) × (value of an hour) × travellers
value of an hour  = clamp(budget ÷ (travellers × days) ÷ 12, ₹60, ₹1,500)
daylight lost     = 2 h for an overnight bus/train (7–16 h), else the journey time
```

It picks the mode with the lowest generalised cost. A tight budget therefore favours a slow bus, while a generous one favours flying.

## Feasibility filters (hard rules)

A place is dropped before scoring if any of these is true:
- it's under 40 km from the start;
- it's closed in the travel month;
- the trip is shorter than its minimum days;
- no transport mode is available;
- time on the ground is under 45% of waking hours (12 per day);
- it's a day trip with more than 4 hours each way and no overnight option.

## Costing

Every line is the sum of simple, documented rates, scaled by the destination's cost index and the season:
- **Stay:** rate × rooms × nights. Rooms by group: solo/couple 1; family ⌈n/3⌉; friends ⌈n/2⌉; dorm beds for budget solo/friends.
- **Food:** rate × people × days.
- **Local transport:** per person on a budget, per vehicle otherwise.
- **Activities:** destination rate × comfort factor × pace factor × days on the ground.
- **Buffer:** 5%, with the total rounded to ₹100.

"Auto" comfort is a top-down search: try premium, then standard, then budget, and keep the first that leaves about 3% headroom.

## Scoring and ranking

A weighted sum of six normalised factors:

| Factor | Weight |
|---|---|
| Trip-type match | 0.36 |
| Season | 0.18 |
| Weather vs preference | 0.13 |
| Crowds | 0.09 |
| Travel effort | 0.16 |
| Budget use | 0.08 |

Small adjustments are added for group type, the famous/hidden preference, and over-long stays. The result is clamped to 1–99.

**Selection** is greedy with a diversity penalty: pick the highest score, subtract 6 points from remaining places in the same state, repeat for 3 picks. "Mix" then swaps in the best hidden gem if one is within 15 points of the top.

**Suggestions** come from re-running the planner with one input changed (fewer days, cheaper comfort, more days). A suggestion is shown only when the change actually produces a fitting trip.

## Verifying AI suggestions

Each proposed place goes through a short decision list, cheapest and most precise evidence first:
1. The Wikipedia article's coordinates, if within 50 km of the model's claim.
2. The nearest gazetteer hit for the place or its base town, if within 50 km.
3. OpenStreetMap search (only for places still unverified; one request per second through a single-thread scheduler).
4. A same-name, same-state hit within ~150 km keeps the model's coordinates; otherwise the place is dropped.

All lookups for a batch run in parallel (`CompletableFuture`), with a 22-second ceiling per place. Duplicates within 12 km are removed.

## Resilience

- **Model fallback chain with cool-down:** models are tried in order. A timeout, 5xx/429 or 404 puts a model on a 5-minute cool-down (`ConcurrentHashMap<model, until>`). The total time is capped at 80 seconds.
- **Caches:**
  - AI results: 6 hours per similar search (budget bucketed logarithmically, 1.25×).
  - Geocoding, climate and Wikipedia: in memory, size-capped.
  - Live forecast: 60 minutes.
- **Rate limiting:** a fixed one-minute window per IP and endpoint group. Memory is bounded by sweeping stale entries.

## Data structures

| Structure | Used for |
|---|---|
| Java records | Immutable request, response and catalogue types (`Destination`, `Trip`, `Cost`, …) |
| `HashMap` / `LinkedHashMap` | Catalogue and alias indexes; ordered JSON output |
| `Set<Integer>` | Months (best / avoid / peak / closed) |
| `ConcurrentHashMap` | Caches, rate-limit counters, model cool-downs |
| `CompletableFuture` | Parallel verification and enrichment |

## Measured (November 2026)

- Planner over 40 catalogue places: a few milliseconds per search.
- AI discovery with `gemini-flash-lite-latest`: about 9–11 s for 8 places, and about 12–15 s end to end including verification.
- Verified suggestions in live tests: 7 or 8 of 8.
