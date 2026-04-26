package com.margdarshak.ai.controller;

import com.margdarshak.ai.config.WhatsAppConfig;
import com.margdarshak.ai.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppController {

    private final WhatsAppConfig whatsAppConfig;
    private final WhatsAppService whatsAppService;

    /**
     * Meta verification handshake. Called once when you register the webhook URL.
     * Meta sends hub.mode=subscribe, hub.verify_token=<your token>, hub.challenge=<random string>.
     * We validate the token and echo back the challenge.
     */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && whatsAppConfig.getVerifyToken().equals(token)) {
            log.info("Webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Webhook verification failed: mode={}, token mismatch={}", mode, !whatsAppConfig.getVerifyToken().equals(token));
        return ResponseEntity.status(403).body("Verification failed");
    }

    /**
     * Receives incoming WhatsApp messages from Meta.
     * Returns 200 immediately — actual processing happens async.
     *
     * Meta's payload structure:
     * { "object": "whatsapp_business_account", "entry": [{ "changes": [{ "value": { "messages": [{ "from": "91...", "text": { "body": "..." } }] } }] }] }
     */
    @PostMapping
    public ResponseEntity<Void> receiveMessage(@RequestBody Map<String, Object> body) {
        log.debug("Webhook POST received: {}", body);

        try {
            if (!"whatsapp_business_account".equals(body.get("object"))) {
                return ResponseEntity.status(404).build();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entry");
            if (entries == null) return ResponseEntity.ok().build();

            for (Map<String, Object> entry : entries) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
                if (changes == null) continue;

                for (Map<String, Object> change : changes) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> value = (Map<String, Object>) change.get("value");
                    if (value == null) continue;

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> messages = (List<Map<String, Object>>) value.get("messages");
                    if (messages == null) continue;

                    for (Map<String, Object> message : messages) {
                        String from = (String) message.get("from");
                        String type = (String) message.get("type");

                        if (!"text".equals(type)) {
                            log.debug("Skipping non-text message type={} from={}", type, from);
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, String> textObj = (Map<String, String>) message.get("text");
                        String text = textObj != null ? textObj.get("body") : null;

                        if (from != null && text != null && !text.isBlank()) {
                            whatsAppService.processIncomingAsync(from, text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing webhook payload", e);
        }

        return ResponseEntity.ok().build();
    }
}
