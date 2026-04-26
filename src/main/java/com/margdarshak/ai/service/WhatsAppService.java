package com.margdarshak.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.margdarshak.ai.config.WhatsAppConfig;
import com.margdarshak.ai.dto.ChatResponse;
import com.margdarshak.ai.dto.ExplainerResult;
import com.margdarshak.ai.dto.FestivalResult;
import com.margdarshak.ai.dto.MuhurtaResult;
import com.margdarshak.ai.model.Intent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final ChatService chatService;
    private final WhatsAppConfig whatsAppConfig;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Async
    public void processIncomingAsync(String phone, String text) {
        try {
            log.info("Processing WhatsApp message from={}: '{}'", phone, text);

            ChatResponse response = chatService.processMessage(phone, text);
            String replyText = formatResponse(response);

            sendReply(phone, replyText);

            log.info("Reply sent to={}, intent={}", phone, response.getIntent());
        } catch (Exception e) {
            log.error("Failed to process WhatsApp message from={}", phone, e);
            sendReply(phone, "Namaste! I'm having trouble processing your request right now. Please try again in a moment.");
        }
    }

    public void sendReply(String phone, String text) {
        try {
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", phone,
                    "type", "text",
                    "text", Map.of("body", text)
            );

            String response = restClient.post()
                    .uri(whatsAppConfig.getMessagesUrl())
                    .header("Authorization", "Bearer " + whatsAppConfig.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.debug("WhatsApp API response: {}", response);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp reply to={}", phone, e);
        }
    }

    public String formatResponse(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return "Namaste! Something went wrong. Please try again.";
        }

        Intent intent = response.getIntent();
        if (intent == null) intent = Intent.UNKNOWN;

        return switch (intent) {
            case MUHURTA -> formatMuhurta(response.getResult());
            case FESTIVAL_GUIDE -> formatFestival(response.getResult());
            case ASTROLOGY_EXPLAINER -> formatExplainer(response.getResult());
            case UNKNOWN -> formatUnknown(response.getResult());
        };
    }

    private String formatMuhurta(Object result) {
        try {
            MuhurtaResult r = objectMapper.convertValue(result, MuhurtaResult.class);
            StringBuilder sb = new StringBuilder();
            sb.append("*Muhurta*\n\n");
            if (r.getStatus() != null) sb.append("Status: ").append(r.getStatus()).append("\n");
            if (r.getTimeWindow() != null) sb.append("Time: ").append(r.getTimeWindow()).append("\n\n");
            if (r.getMessage() != null) sb.append(r.getMessage()).append("\n\n");
            if (r.getReason() != null) sb.append("_").append(r.getReason()).append("_");
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to format MuhurtaResult, falling back to generic", e);
            return formatGeneric(result);
        }
    }

    private String formatFestival(Object result) {
        try {
            FestivalResult r = objectMapper.convertValue(result, FestivalResult.class);
            StringBuilder sb = new StringBuilder();
            if (r.getFestivalName() != null) sb.append("*").append(r.getFestivalName()).append("*\n\n");

            List<FestivalResult.Step> steps = r.getSteps();
            if (steps != null && !steps.isEmpty()) {
                for (FestivalResult.Step step : steps) {
                    sb.append(step.getStepNumber()).append(". ").append(step.getAction());
                    if (step.getReason() != null) sb.append("\n   _").append(step.getReason()).append("_");
                    sb.append("\n");
                }
                sb.append("\n");
            }

            List<String> items = r.getRequiredItems();
            if (items != null && !items.isEmpty()) {
                sb.append("*Required items:*\n");
                for (String item : items) {
                    sb.append("  - ").append(item).append("\n");
                }
                sb.append("\n");
            }

            List<String> warnings = r.getWarnings();
            if (warnings != null && !warnings.isEmpty()) {
                sb.append("*Note:*\n");
                for (String w : warnings) {
                    sb.append("  - ").append(w).append("\n");
                }
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to format FestivalResult, falling back to generic", e);
            return formatGeneric(result);
        }
    }

    private String formatExplainer(Object result) {
        try {
            ExplainerResult r = objectMapper.convertValue(result, ExplainerResult.class);
            StringBuilder sb = new StringBuilder();
            sb.append("*Astrology Insight*\n\n");
            if (r.getExplanation() != null) sb.append(r.getExplanation()).append("\n\n");
            if (r.getReassurance() != null) sb.append("_").append(r.getReassurance()).append("_\n\n");

            List<String> remedies = r.getRemedies();
            if (remedies != null && !remedies.isEmpty()) {
                sb.append("*Remedies:*\n");
                for (int i = 0; i < remedies.size(); i++) {
                    sb.append(i + 1).append(". ").append(remedies.get(i)).append("\n");
                }
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to format ExplainerResult, falling back to generic", e);
            return formatGeneric(result);
        }
    }

    @SuppressWarnings("unchecked")
    private String formatUnknown(Object result) {
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;
            Object msg = map.get("message");
            if (msg != null) return msg.toString();
        }
        return "Namaste! I can help you with:\n"
                + "1. Finding auspicious times (Muhurta) for activities\n"
                + "2. Festival and puja guides\n"
                + "3. Understanding life challenges through astrology\n\n"
                + "Please try rephrasing your question around one of these topics.";
    }

    private String formatGeneric(Object result) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }
}
