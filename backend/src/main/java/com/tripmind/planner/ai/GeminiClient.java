package com.tripmind.planner.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Gemini via the Generative Language REST API, in JSON output mode.
 *
 * <p>Popular models are sometimes overloaded (requests hang or return 503 "high demand"), so this client
 * walks a chain of models: fast "lite" models first, then a larger one. A model that times out or reports
 * overload is skipped for a few minutes so travellers don't keep waiting on it. Overall time is capped.</p>
 */
@Component
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final long COOL_DOWN_MS = 5 * 60 * 1000L;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${gemini.api.key:}")
    private String apiKey;

    /** Optional single preferred model (GEMINI_MODEL); tried first if set. */
    @Value("${gemini.model:}")
    private String preferredModel;

    /** Fallback chain, fastest first. Measured: lite models answer the discovery prompt in ~9-10 s. */
    @Value("${gemini.models:gemini-flash-lite-latest,gemini-3.1-flash-lite,gemini-3.5-flash}")
    private String modelChain;

    /** Upper bound for the whole chain, in seconds. */
    @Value("${gemini.timeout-seconds:80}")
    private int totalTimeoutSeconds;

    private final Map<String, Long> coolingUntil = new ConcurrentHashMap<>();
    private volatile String lastError;
    private volatile String lastModel;

    public GeminiClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank() && !"demo".equals(apiKey);
    }

    @Override
    public String lastError() { return lastError; }

    @Override
    public String lastModel() { return lastModel; }

    List<String> chain() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (preferredModel != null && !preferredModel.isBlank()) models.add(preferredModel.trim());
        Arrays.stream(modelChain.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(models::add);
        return new ArrayList<>(models);
    }

    @Override
    public Optional<String> json(String prompt) {
        if (!enabled()) return Optional.empty();
        long deadline = System.currentTimeMillis() + totalTimeoutSeconds * 1000L;
        List<String> failures = new ArrayList<>();
        List<String> models = chain();
        long now = System.currentTimeMillis();
        // Skip models that recently failed, unless every model is cooling down.
        List<String> ready = models.stream().filter(m -> coolingUntil.getOrDefault(m, 0L) < now).toList();
        if (ready.isEmpty()) ready = models;

        for (String model : ready) {
            long remainingMs = deadline - System.currentTimeMillis();
            int perModel = model.contains("lite") ? 30 : 60;
            int timeout = (int) Math.min(perModel, remainingMs / 1000);
            if (timeout < 8) { failures.add(model + ": skipped (out of time)"); break; }

            Attempt a = call(prompt, model, timeout);
            if (a.text != null) {
                lastModel = model;
                lastError = failures.isEmpty() ? null : "used " + model + " after: " + String.join("; ", failures);
                return Optional.of(a.text);
            }
            failures.add(model + ": " + a.error);
            if (a.coolDown) coolingUntil.put(model, System.currentTimeMillis() + COOL_DOWN_MS);
        }
        lastModel = null;
        lastError = String.join("; ", failures);
        log.warn("All Gemini models failed: {}", lastError);
        return Optional.empty();
    }

    private record Attempt(String text, String error, boolean coolDown) {
        static Attempt ok(String t) { return new Attempt(t, null, false); }
        static Attempt fail(String e, boolean coolDown) { return new Attempt(null, e, coolDown); }
    }

    private Attempt call(String prompt, String model, int timeoutSeconds) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
            ObjectNode cfg = body.putObject("generationConfig");
            cfg.put("temperature", 0.8);
            cfg.put("maxOutputTokens", 16384);
            cfg.put("responseMimeType", "application/json");
            // 2.5 Flash thinks by default, which only costs time here.
            if (model.startsWith("gemini-2.5-flash") && !model.contains("lite")) {
                cfg.putObject("thinkingConfig").put("thinkingBudget", 0);
            }

            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            long t0 = System.currentTimeMillis();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                String detail = "";
                try { detail = mapper.readTree(res.body()).path("error").path("message").asText(""); } catch (Exception ignored) {}
                int s = res.statusCode();
                boolean transientError = s == 429 || s == 503 || s >= 500;
                boolean gone = s == 404;
                log.warn("Gemini {} returned HTTP {}: {}", model, s, shorten(detail));
                return Attempt.fail("http_" + s + (detail.isEmpty() ? "" : " " + shorten(detail)), transientError || gone);
            }
            JsonNode candidate = mapper.readTree(res.body()).path("candidates").path(0);
            StringBuilder text = new StringBuilder();
            for (JsonNode p : candidate.path("content").path("parts")) text.append(p.path("text").asText(""));
            String out = text.toString().trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            String finish = candidate.path("finishReason").asText("");
            if (out.isEmpty()) return Attempt.fail("empty reply" + (finish.isEmpty() ? "" : " (" + finish + ")"), false);
            if ("MAX_TOKENS".equals(finish)) return Attempt.fail("reply cut off (MAX_TOKENS)", false);
            log.info("Gemini {} answered in {} ms", model, System.currentTimeMillis() - t0);
            return Attempt.ok(out);
        } catch (HttpTimeoutException e) {
            return Attempt.fail("timeout after " + timeoutSeconds + "s", true);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Attempt.fail("error " + shorten(String.valueOf(e.getMessage())), true);
        }
    }

    private static String shorten(String s) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 120 ? one.substring(0, 120) + "…" : one;
    }
}
