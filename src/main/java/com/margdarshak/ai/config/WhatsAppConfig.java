package com.margdarshak.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "whatsapp")
@Getter
@Setter
public class WhatsAppConfig {

    private String verifyToken;
    private String accessToken;
    private String phoneNumberId;
    private String graphApiVersion = "v25.0";

    public String getMessagesUrl() {
        return String.format(
                "https://graph.facebook.com/%s/%s/messages",
                graphApiVersion,
                phoneNumberId
        );
    }
}
