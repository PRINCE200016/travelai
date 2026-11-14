package com.tripmind.planner.ai;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Open-data facts used to verify and enrich places the AI suggests. Implementations never throw. */
public interface PlaceLookup {

    record GeoHit(String name, String state, double lat, double lon, Integer population, Double elevation) {}

    /** Typical conditions for a month, from last year's daily observations. */
    record Climate(int highC, int lowC, int rainyDays) {}

    record WikiInfo(String title, String imageUrl, String extract, String url, Double lat, Double lon) {}

    /** Candidate matches for a place name in India, best first. */
    CompletableFuture<List<GeoHit>> geocode(String name);

    CompletableFuture<Optional<Climate>> climate(double lat, double lon, int month);

    CompletableFuture<Optional<WikiInfo>> wiki(String title);

    /**
     * Slower, more complete gazetteer (OpenStreetMap) for small villages and valleys. Used only as a last resort,
     * so implementations may throttle heavily.
     */
    default CompletableFuture<List<GeoHit>> searchDetailed(String query) {
        return CompletableFuture.completedFuture(List.of());
    }
}
