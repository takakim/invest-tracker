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
        int timeoutSeconds,
        List<String> availableProviders
) {
    public static final List<String> DEFAULT_PROVIDERS = List.of("LM_STUDIO", "OPENAI", "GEMINI", "ANTHROPIC");

    public AiStatusDto(
            boolean enabled,
            boolean connected,
            String provider,
            String baseUrl,
            String configuredModel,
            List<String> availableModels,
            String errorMessage,
            int timeoutSeconds
    ) {
        this(enabled, connected, provider, baseUrl, configuredModel, availableModels, errorMessage, timeoutSeconds, DEFAULT_PROVIDERS);
    }

    public AiStatusDto(
            boolean enabled,
            boolean connected,
            String provider,
            String baseUrl,
            String configuredModel,
            List<String> availableModels,
            String errorMessage
    ) {
        this(enabled, connected, provider, baseUrl, configuredModel, availableModels, errorMessage, 60, DEFAULT_PROVIDERS);
    }
}

