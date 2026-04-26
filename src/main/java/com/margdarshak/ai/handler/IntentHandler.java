package com.margdarshak.ai.handler;

import com.margdarshak.ai.dto.ChatResponse;
import com.margdarshak.ai.model.Intent;

public interface IntentHandler {

    Intent supportedIntent();

    ChatResponse handle(String userMessage);
}
