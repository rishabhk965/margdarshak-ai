package com.margdarshak.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.margdarshak.ai.service.AiService;
import com.margdarshak.ai.service.ClaudeAiService;
import com.margdarshak.ai.service.GeminiAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AiConfig {

    @Bean
    AiService aiService(
            @Value("${ai.active-provider:gemini}") String provider,
            @Value("${anthropic.api-key:}") String anthropicKey,
            @Value("${gemini.api-key:}") String geminiKey) {

        log.info("Initializing AI provider: {}", provider);

        return switch (provider.toLowerCase()) {
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
                    "Unknown ai.active-provider='" + provider + "'. Supported: claude, gemini");
        };
    }
}
