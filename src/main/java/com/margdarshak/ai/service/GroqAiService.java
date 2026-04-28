package com.margdarshak.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Groq Cloud LLM via the OpenAI-compatible Chat Completions API.
 *
 * @see <a href="https://console.groq.com/docs/api-reference">Groq API reference</a>
 */
@Slf4j
public class GroqAiService implements AiService {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";

    private final String apiKey;
    private final String model;
    private final String fastModel;
    private final RestClient restClient;

    public GroqAiService(String apiKey, String model, String fastModel) {
        this.apiKey = apiKey;
        this.model = model;
        this.fastModel = fastModel;
        this.restClient = RestClient.create();
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, model, 2048);
    }

    @Override
    public String generateFast(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, fastModel, 512);
    }

    private String call(String systemPrompt, String userMessage, String modelName, int maxTokens) {
        log.debug("Calling Groq model={} maxTokens={}", modelName, maxTokens);

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)),
                "max_tokens", maxTokens);

        JsonNode response = restClient.post()
                .uri(BASE_URL + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .body(Objects.requireNonNull(requestBody))
                .retrieve()
                .body(JsonNode.class);

        return extractMessageContent(response);
    }

    private String extractMessageContent(JsonNode response) {
        if (response == null) {
            return "";
        }
        JsonNode choices = response.path("choices");
        if (choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode content = choices.get(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? "" : content.asText("");
    }
}
