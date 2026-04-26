package com.margdarshak.ai.service;

import com.margdarshak.ai.model.Intent;

public interface IntentClassifier {

    Intent classify(String userMessage);
}
