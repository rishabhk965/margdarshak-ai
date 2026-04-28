package com.margdarshak.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.margdarshak.ai.service.AiService;
import com.margdarshak.ai.service.ClaudeAiService;
import com.margdarshak.ai.service.GeminiAiService;
import com.margdarshak.ai.service.GroqAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AiConfig {

    @Bean
    AiService aiService(
            @Value("${ai.active-provider:groq}") String provider,
            @Value("${anthropic.api-key:}") String anthropicKey,
            @Value("${gemini.api-key:}") String geminiKey,
            @Value("${groq.api-key:}") String groqKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String groqModel,
            @Value("${groq.fast-model:llama-3.1-8b-instant}") String groqFastModel) {

        log.info("Initializing AI provider: {}", provider);

        return switch (provider.toLowerCase()) {
            case "groq" -> {
                if (groqKey.isBlank()) {
                    throw new IllegalStateException(
                            "ai.active-provider=groq but groq.api-key is not configured");
                }
                yield new GroqAiService(groqKey, groqModel, groqFastModel);
            }
            case "claude" -> {
                if (anthropicKey.isBlank()) {
                    throw new IllegalStateException(
                            "ai.active-provider=claude but anthropic.api-key is not configured");
                }
                AnthropicClient client = AnthropicOkHttpClient.builder()
                        .apiKey(anthropicKey)
                        .build();
                yield new ClaudeAiService(client);
            }
            case "gemini" -> {
                if (geminiKey.isBlank()) {
                    throw new IllegalStateException(
                            "ai.active-provider=gemini but gemini.api-key is not configured");
                }
                yield new GeminiAiService(geminiKey);
            }
            default -> throw new IllegalStateException(
                    "Unknown ai.active-provider='" + provider + "'. Supported: groq, claude, gemini");
        };
    }
}
