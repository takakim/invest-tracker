package com.takakim.investtracker.service.ai.dto;

import java.util.List;

public record AiStatusDto(
        boolean enabled,
        boolean connected,
        String provider,
        String baseUrl,
        String configuredModel,
        List<String> availableModels,
        String errorMessage
) {
}
