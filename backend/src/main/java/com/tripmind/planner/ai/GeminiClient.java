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

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    @Value("${gemini.timeout-seconds:40}")
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
        try {
            ObjectNode body = mapper.createObjectNode();
            body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
            ObjectNode cfg = body.putObject("generationConfig");
            cfg.put("temperature", 0.8);
            cfg.put("maxOutputTokens", 8192);
            cfg.put("responseMimeType", "application/json");

            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Gemini returned HTTP {}", res.statusCode());
                return Optional.empty();
            }
            JsonNode parts = mapper.readTree(res.body()).path("candidates").path(0).path("content").path("parts");
            StringBuilder text = new StringBuilder();
            for (JsonNode p : parts) text.append(p.path("text").asText(""));
            String out = text.toString().trim()
                    .replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Gemini call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
