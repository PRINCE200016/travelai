package com.tripmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live daily forecast from Open-Meteo (free, no API key) for trips starting within its 16-day window.
 * Further out, the planner's monthly climate figures are the honest answer, so this returns nothing.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int HORIZON_DAYS = 15;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(List<Map<String, Object>> days, Instant at) {}

    public WeatherService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> forecast(double lat, double lon, LocalDate start, int days) {
        LocalDate today = LocalDate.now(IST);
        if (start.isBefore(today) || start.isAfter(today.plusDays(HORIZON_DAYS))) return List.of();
        String key = Math.round(lat * 50) + ":" + Math.round(lon * 50);
        Cached hit = cache.get(key);
        List<Map<String, Object>> all;
        if (hit != null && hit.at().isAfter(Instant.now().minus(Duration.ofMinutes(60)))) {
            all = hit.days();
        } else {
            all = fetch(lat, lon);
            if (!all.isEmpty()) {
                if (cache.size() > 2000) cache.clear();
                cache.put(key, new Cached(all, Instant.now()));
            }
        }
        LocalDate end = start.plusDays(Math.max(1, days));
        return all.stream().filter(d -> {
            LocalDate date = LocalDate.parse((String) d.get("date"));
            return !date.isBefore(start) && date.isBefore(end);
        }).toList();
    }

    private List<Map<String, Object>> fetch(double lat, double lon) {
        try {
            URI uri = URI.create("https://api.open-meteo.com/v1/forecast?timezone=Asia%2FKolkata&forecast_days=16"
                    + "&latitude=" + lat + "&longitude=" + lon
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max");
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "TripMind/1.0 (+https://tripmind-ai.com)").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Forecast API returned {}", res.statusCode());
                return List.of();
            }
            JsonNode d = mapper.readTree(res.body()).path("daily");
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < d.path("time").size(); i++) {
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", d.path("time").path(i).asText());
                day.put("minC", (int) Math.round(d.path("temperature_2m_min").path(i).asDouble()));
                day.put("maxC", (int) Math.round(d.path("temperature_2m_max").path(i).asDouble()));
                day.put("rainChance", d.path("precipitation_probability_max").path(i).isNull() ? null
                        : d.path("precipitation_probability_max").path(i).asInt());
                day.put("desc", describe(d.path("weather_code").path(i).asInt(-1)));
                out.add(day);
            }
            return out;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Forecast lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** WMO weather interpretation codes. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "Mixed";
    }
}
