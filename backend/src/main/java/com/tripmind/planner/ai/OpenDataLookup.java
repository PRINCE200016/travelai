package com.tripmind.planner.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Free, keyless open data:
 * <ul>
 *   <li>Open-Meteo geocoding (GeoNames) to confirm a place exists and get real coordinates,</li>
 *   <li>Open-Meteo archive for last year's observed weather in the travel month,</li>
 *   <li>Wikipedia page summaries for a photo and a neutral description.</li>
 * </ul>
 * Only place names and coordinates are sent; never anything about the user.
 */
@Component
public class OpenDataLookup implements PlaceLookup {

    private static final Logger log = LoggerFactory.getLogger(OpenDataLookup.class);
    private static final String UA = "TripMind/1.0 (+https://tripmind-ai.com)";
    private static final int CACHE_MAX = 5000;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Map<String, List<GeoHit>> geoCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<Climate>> climateCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<WikiInfo>> wikiCache = new ConcurrentHashMap<>();

    public OpenDataLookup(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletableFuture<List<GeoHit>> geocode(String name) {
        String key = name.toLowerCase().trim();
        if (geoCache.containsKey(key)) return CompletableFuture.completedFuture(geoCache.get(key));
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&countryCode=IN&name=" + enc(name);
        return get(url, json -> {
            List<GeoHit> out = new ArrayList<>();
            for (JsonNode r : json.path("results")) {
                out.add(new GeoHit(r.path("name").asText(), r.path("admin1").asText(""),
                        r.path("latitude").asDouble(), r.path("longitude").asDouble(),
                        r.hasNonNull("population") ? r.path("population").asInt() : null,
                        r.hasNonNull("elevation") ? r.path("elevation").asDouble() : null));
            }
            return out;
        }, List.<GeoHit>of()).thenApply(hits -> { put(geoCache, key, hits); return hits; });
    }

    @Override
    public CompletableFuture<Optional<Climate>> climate(double lat, double lon, int month) {
        String key = Math.round(lat * 10) + ":" + Math.round(lon * 10) + ":" + month;
        if (climateCache.containsKey(key)) return CompletableFuture.completedFuture(climateCache.get(key));
        // Most recent fully observed occurrence of that month.
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        int year = month < today.getMonthValue() ? today.getYear() : today.getYear() - 1;
        YearMonth ym = YearMonth.of(year, month);
        String url = "https://archive-api.open-meteo.com/v1/archive?timezone=Asia%2FKolkata"
                + "&latitude=" + lat + "&longitude=" + lon
                + "&start_date=" + ym.atDay(1) + "&end_date=" + ym.atEndOfMonth()
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum";
        return get(url, json -> {
            JsonNode d = json.path("daily");
            double hi = avg(d.path("temperature_2m_max")), lo = avg(d.path("temperature_2m_min"));
            if (Double.isNaN(hi) || Double.isNaN(lo)) return Optional.<Climate>empty();
            int rainy = 0;
            for (JsonNode p : d.path("precipitation_sum")) if (!p.isNull() && p.asDouble() >= 2.5) rainy++;
            return Optional.of(new Climate((int) Math.round(hi), (int) Math.round(lo), rainy));
        }, Optional.<Climate>empty()).thenApply(c -> { put(climateCache, key, c); return c; });
    }

    @Override
    public CompletableFuture<Optional<WikiInfo>> wiki(String title) {
        if (title == null || title.isBlank()) return CompletableFuture.completedFuture(Optional.empty());
        String key = title.trim();
        if (wikiCache.containsKey(key)) return CompletableFuture.completedFuture(wikiCache.get(key));
        String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + enc(key.replace(' ', '_')).replace("+", "%20");
        return get(url, json -> {
            if (!"standard".equals(json.path("type").asText())) return Optional.<WikiInfo>empty();
            String image = null;
            JsonNode orig = json.path("originalimage");
            JsonNode thumb = json.path("thumbnail");
            if (orig.hasNonNull("source")) {
                int w = orig.path("width").asInt(0);
                String src = orig.path("source").asText();
                boolean rasterOk = src.matches("(?i).+\\.(jpe?g|png|webp)$");
                if (rasterOk && w > 0 && w <= 2400) image = src;
                else if (thumb.hasNonNull("source") && w >= 1280) image = thumb.path("source").asText().replaceFirst("/\\d+px-", "/1280px-");
                else if (thumb.hasNonNull("source")) image = thumb.path("source").asText();
            }
            JsonNode c = json.path("coordinates");
            return Optional.of(new WikiInfo(json.path("title").asText(key), image,
                    json.path("extract").asText(""), json.path("content_urls").path("desktop").path("page").asText(null),
                    c.hasNonNull("lat") ? c.path("lat").asDouble() : null,
                    c.hasNonNull("lon") ? c.path("lon").asDouble() : null));
        }, Optional.<WikiInfo>empty()).thenApply(w -> { put(wikiCache, key, w); return w; });
    }

    /**
     * OpenStreetMap Nominatim. Its usage policy allows at most one request per second, so calls are queued
     * one at a time; anything that would wait more than ~10 s is skipped instead.
     */
    @Override
    public CompletableFuture<List<GeoHit>> searchDetailed(String query) {
        String key = "osm:" + query.toLowerCase().trim();
        if (geoCache.containsKey(key)) return CompletableFuture.completedFuture(geoCache.get(key));
        long delay = reserveOsmSlot();
        if (delay < 0) return CompletableFuture.completedFuture(List.of());
        String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&countrycodes=in&limit=5&q=" + enc(query);
        CompletableFuture<List<GeoHit>> out = new CompletableFuture<>();
        osmQueue.schedule(() -> get(url, json -> {
            List<GeoHit> hits = new ArrayList<>();
            for (JsonNode r : json) {
                JsonNode a = r.path("address");
                String name = r.path("name").asText("");
                if (name.isBlank()) name = r.path("display_name").asText("").split(",")[0];
                hits.add(new GeoHit(name, a.path("state").asText(""), r.path("lat").asDouble(), r.path("lon").asDouble(), null, null));
            }
            return hits;
        }, List.<GeoHit>of()).whenComplete((hits, err) -> {
            List<GeoHit> h = hits == null ? List.of() : hits;
            put(geoCache, key, h);
            out.complete(h);
        }), delay, java.util.concurrent.TimeUnit.MILLISECONDS);
        return out;
    }

    private final java.util.concurrent.ScheduledExecutorService osmQueue =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "osm-queue");
                t.setDaemon(true);
                return t;
            });
    private long nextOsmAt = 0;

    /** Milliseconds to wait for the next 1-per-second slot, or -1 if the queue is too long. */
    private synchronized long reserveOsmSlot() {
        long now = System.currentTimeMillis();
        long at = Math.max(now, nextOsmAt);
        if (at - now > 10_000) return -1;
        nextOsmAt = at + 1100;
        return at - now;
    }

    // ------------------------------------------------------------------

    private <T> CompletableFuture<T> get(String url, Function<JsonNode, T> parse, T fallback) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", UA).header("Accept", "application/json").GET().build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() != 200) return fallback;
                    try { return parse.apply(mapper.readTree(res.body())); }
                    catch (Exception e) { return fallback; }
                })
                .exceptionally(e -> {
                    log.debug("Open data lookup failed for {}: {}", url, e.getMessage());
                    return fallback;
                });
    }

    private static double avg(JsonNode arr) {
        double sum = 0; int n = 0;
        for (JsonNode v : arr) if (!v.isNull()) { sum += v.asDouble(); n++; }
        return n == 0 ? Double.NaN : sum / n;
    }

    private static <V> void put(Map<String, V> cache, String key, V value) {
        if (cache.size() > CACHE_MAX) cache.clear();
        cache.put(key, value);
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
