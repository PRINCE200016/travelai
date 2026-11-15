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
import java.time.Duration;
import java.util.Optional;

/** Google Gemini via the Generative Language REST API, in JSON output mode. */
@Component
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String model;

    /** The model actually in use; replaced once if Google retires the configured one and names a successor. */
    private volatile String activeModel;

    private static final java.util.regex.Pattern SUGGESTED_MODEL =
            java.util.regex.Pattern.compile("use models/([A-Za-z0-9.\\-]+)");

    @Value("${gemini.timeout-seconds:60}")
    private int timeoutSeconds;

    public GeminiClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank() && !"demo".equals(apiKey);
    }

    @Override
    public Optional<String> json(String prompt) {
        if (!enabled()) return Optional.empty();
        if (activeModel == null) activeModel = model;
        return call(prompt, activeModel, true);
    }

    private Optional<String> call(String prompt, String model, boolean mayFollowSuggestion) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
            ObjectNode cfg = body.putObject("generationConfig");
            cfg.put("temperature", 0.8);
            cfg.put("maxOutputTokens", 16384);
            cfg.put("responseMimeType", "application/json");
            // 2.5 Flash "thinks" by default; thinking tokens eat the output budget and time. Not needed here.
            if (model.startsWith("gemini-2.5-flash")) cfg.putObject("thinkingConfig").put("thinkingBudget", 0);

            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                String detail = mapper.readTree(res.body()).path("error").path("message").asText("");
                lastError = "http_" + res.statusCode() + " (" + model + ")" + (detail.isEmpty() ? "" : ": " + shorten(detail));
                log.warn("Gemini returned {}", lastError);
                // A retired model: Google's error names its successor. Switch once and retry.
                java.util.regex.Matcher m = SUGGESTED_MODEL.matcher(detail);
                if (mayFollowSuggestion && (res.statusCode() == 404 || res.statusCode() == 400) && m.find()
                        && !m.group(1).equals(model)) {
                    String next = m.group(1);
                    log.warn("Gemini model {} is unavailable; switching to suggested {}", model, next);
                    activeModel = next;
                    return call(prompt, next, false);
                }
                return Optional.empty();
            }
            JsonNode candidate = mapper.readTree(res.body()).path("candidates").path(0);
            JsonNode parts = candidate.path("content").path("parts");
            StringBuilder text = new StringBuilder();
            for (JsonNode p : parts) text.append(p.path("text").asText(""));
            String out = text.toString().trim()
                    .replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            String finish = candidate.path("finishReason").asText("");
            if (out.isEmpty()) {
                lastError = "empty_response" + (finish.isEmpty() ? "" : " (" + finish + ")");
                log.warn("Gemini {}", lastError);
                return Optional.empty();
            }
            lastError = "MAX_TOKENS".equals(finish) ? "truncated (MAX_TOKENS)" : null;
            return Optional.of(out);
        } catch (java.net.http.HttpTimeoutException e) {
            lastError = "timeout after " + timeoutSeconds + "s";
            log.warn("Gemini {}", lastError);
            return Optional.empty();
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            lastError = "error: " + shorten(String.valueOf(e.getMessage()));
            log.warn("Gemini call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private volatile String lastError;

    @Override
    public String lastError() { return lastError; }

    private static String shorten(String s) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 160 ? one.substring(0, 160) + "…" : one;
    }
}
