package com.takakim.investtracker.service.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AiConfigRequest(
        @Min(value = 5, message = "timeoutSeconds must be at least 5")
        @Max(value = 3600, message = "timeoutSeconds cannot exceed 3600")
        Integer timeoutSeconds,
        String provider,
        String model
) {
    public AiConfigRequest(Integer timeoutSeconds) {
        this(timeoutSeconds, null, null);
    }
}
