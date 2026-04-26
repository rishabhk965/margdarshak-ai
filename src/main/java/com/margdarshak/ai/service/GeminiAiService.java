package com.margdarshak.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class GeminiAiService implements AiService {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String POWERFUL_MODEL = "gemini-2.0-flash";
    // Lite model is ~3x cheaper and ~2x faster than flash. Plenty smart enough for
    // a single-token intent label classification.
    private static final String FAST_MODEL = "gemini-2.5-flash-lite";

    private final String apiKey;
    private final RestClient restClient;

    public GeminiAiService(String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create();
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, POWERFUL_MODEL, 2048);
    }

    @Override
    public String generateFast(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, FAST_MODEL, 512);
    }

    private String call(String systemPrompt, String userMessage, String model, int maxOutputTokens) {
        log.debug("Calling Gemini model={} maxOutputTokens={}", model, maxOutputTokens);

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userMessage))
                )),
                "generationConfig", Map.of("maxOutputTokens", maxOutputTokens)
        );

        JsonNode response = restClient.post()
                .uri(BASE_URL + model + ":generateContent?key=" + apiKey)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .body(Objects.requireNonNull(requestBody))
                .retrieve()
                .body(JsonNode.class);

        return extractText(response);
    }

    private String extractText(JsonNode response) {
        if (response == null) return "";
        JsonNode candidates = response.path("candidates");
        if (candidates.isMissingNode() || candidates.isEmpty()) return "";
        return candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText("");
    }
}
