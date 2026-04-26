package com.margdarshak.ai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ClaudeAiService implements AiService {

    private final AnthropicClient client;

    @Override
    public String generate(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, Model.CLAUDE_SONNET_4_5, 2048L);
    }

    @Override
    public String generateFast(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, Model.CLAUDE_HAIKU_4_5, 512L);
    }

    private String call(String systemPrompt, String userMessage, Model model, long maxTokens) {
        log.debug("Calling Claude model={} maxTokens={}", model, maxTokens);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .addUserMessage(userMessage)
                .build();

        Message message = client.messages().create(params);

        return message.content().stream()
                .filter(ContentBlock::isText)
                .map(block -> block.asText().text())
                .findFirst()
                .orElse("");
    }
}
