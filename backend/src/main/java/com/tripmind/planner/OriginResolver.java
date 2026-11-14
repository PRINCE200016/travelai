package com.tripmind.planner;

import com.tripmind.planner.Catalog.Origin;
import com.tripmind.planner.ai.PlaceLookup;
import com.tripmind.planner.ai.PlaceLookup.GeoHit;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Any starting point in India: known cities first (fast, typo-tolerant), then the open map for small towns. */
@Component
public class OriginResolver {

    private final Catalog catalog;
    private final PlaceLookup lookup;

    public OriginResolver(Catalog catalog, PlaceLookup lookup) {
        this.catalog = catalog;
        this.lookup = lookup;
    }

    public Optional<Origin> resolve(String raw) {
        Optional<Origin> known = catalog.resolveOrigin(raw);
        if (known.isPresent()) return known;
        if (raw == null || raw.trim().length() < 3) return Optional.empty();

        String name = raw.split(",")[0].trim();
        String statePart = raw.contains(",") ? Catalog.key(raw.substring(raw.indexOf(',') + 1)) : "";
        List<GeoHit> hits;
        try {
            hits = lookup.geocode(name).get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
        return hits.stream()
                .filter(h -> statePart.isEmpty() || Catalog.key(h.state()).contains(statePart) || statePart.contains(Catalog.key(h.state())))
                .max(Comparator.comparingInt(h -> h.population() == null ? 0 : h.population()))
                .map(h -> new Origin(h.name() + (h.state().isBlank() ? "" : ", " + h.state()), h.lat(), h.lon(), false, null));
    }
}
