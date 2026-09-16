package com.takakim.investtracker.service.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AiConfigRequest(
        @NotNull(message = "timeoutSeconds must not be null")
        @Min(value = 5, message = "timeoutSeconds must be at least 5")
        @Max(value = 3600, message = "timeoutSeconds cannot exceed 3600")
        Integer timeoutSeconds
) {
}
