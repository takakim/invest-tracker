package com.takakim.investtracker.service.ai.dto;

import java.util.List;

public record AiStatusDto(
        boolean enabled,
        boolean connected,
        String provider,
        String baseUrl,
        String configuredModel,
        List<String> availableModels,
        String errorMessage,
        int timeoutSeconds
) {
    public AiStatusDto(
            boolean enabled,
            boolean connected,
            String provider,
            String baseUrl,
            String configuredModel,
            List<String> availableModels,
            String errorMessage
    ) {
        this(enabled, connected, provider, baseUrl, configuredModel, availableModels, errorMessage, 60);
    }
}

