package com.takakim.investtracker.service.ai;

import com.takakim.investtracker.config.AiProperties;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Selects the active {@link AiGateway} implementation based on the configured
 * {@code AI_PROVIDER} environment variable (default: AUTO, favouring local).
 *
 * <p>Supported values: {@code AUTO}, {@code LM_STUDIO}, {@code OPENAI}, {@code GEMINI}, {@code ANTHROPIC}.
 */
@Component
public class AiGatewayFactory {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayFactory.class);

    private final AiProperties properties;
    private final LmStudioGateway lmStudioGateway;
    private final OpenAiGateway openAiGateway;
    private final GeminiGateway geminiGateway;
    private final AnthropicGateway anthropicGateway;

    public AiGatewayFactory(AiProperties properties,
                             LmStudioGateway lmStudioGateway,
                             OpenAiGateway openAiGateway,
                             GeminiGateway geminiGateway,
                             AnthropicGateway anthropicGateway) {
        this.properties = properties;
        this.lmStudioGateway = lmStudioGateway;
        this.openAiGateway = openAiGateway;
        this.geminiGateway = geminiGateway;
        this.anthropicGateway = anthropicGateway;
    }

    /**
     * Returns the {@link AiGateway} for the currently configured provider.
     * When provider is AUTO or unset, favours local (LM_STUDIO) if available,
     * and falls back to configured third-party providers if local is offline.
     */
    public AiGateway getActiveGateway() {
        String provider = properties.getProvider();
        if (provider == null || provider.isBlank() || "AUTO".equalsIgnoreCase(provider.trim())) {
            return resolveAutoGateway();
        }
        return switch (provider.toUpperCase().trim()) {
            case "OPENAI" -> {
                log.debug("Using OpenAI gateway");
                yield openAiGateway;
            }
            case "GEMINI" -> {
                log.debug("Using Gemini gateway");
                yield geminiGateway;
            }
            case "ANTHROPIC" -> {
                log.debug("Using Anthropic gateway");
                yield anthropicGateway;
            }
            case "LM_STUDIO" -> {
                log.debug("Using LM Studio gateway (provider=LM_STUDIO)");
                yield lmStudioGateway;
            }
            default -> {
                log.debug("Unknown provider '{}', defaulting to LM Studio", provider);
                yield lmStudioGateway;
            }
        };
    }

    private AiGateway resolveAutoGateway() {
        // Favour local LM Studio if reachable and connected
        try {
            var status = lmStudioGateway.checkStatus();
            if (status != null && status.connected()) {
                log.debug("AUTO provider: Local LM Studio is connected and available, favouring LM_STUDIO");
                return lmStudioGateway;
            }
        } catch (Exception e) {
            log.debug("Local LM Studio availability check failed in AUTO mode: {}", e.getMessage());
        }

        // Fall back to third-party providers with configured API keys
        if (properties.getOpenaiApiKey() != null && !properties.getOpenaiApiKey().isBlank()) {
            log.debug("AUTO provider: LM Studio offline, falling back to OpenAI");
            return openAiGateway;
        }
        if (properties.getGeminiApiKey() != null && !properties.getGeminiApiKey().isBlank()) {
            log.debug("AUTO provider: LM Studio offline, falling back to Gemini");
            return geminiGateway;
        }
        if (properties.getAnthropicApiKey() != null && !properties.getAnthropicApiKey().isBlank()) {
            log.debug("AUTO provider: LM Studio offline, falling back to Anthropic");
            return anthropicGateway;
        }

        log.debug("AUTO provider: Defaulting to LM Studio");
        return lmStudioGateway;
    }

    /**
     * Updates the timeout setting across properties and all registered gateways.
     */
    public void updateTimeout(int timeoutSeconds) {
        log.info("Updating AI gateway timeout to {} seconds", timeoutSeconds);
        properties.setTimeoutSeconds(timeoutSeconds);
        lmStudioGateway.setTimeoutSeconds(timeoutSeconds);
        openAiGateway.setTimeoutSeconds(timeoutSeconds);
        geminiGateway.setTimeoutSeconds(timeoutSeconds);
        anthropicGateway.setTimeoutSeconds(timeoutSeconds);
    }

    /**
     * Updates the active AI provider.
     */
    public void updateProvider(String provider) {
        if (provider != null && !provider.isBlank()) {
            String sanitized = provider.trim().toUpperCase();
            log.info("Updating active AI provider to {}", sanitized);
            properties.setProvider(sanitized);
        }
    }

    /**
     * Updates the target AI model.
     */
    public void updateModel(String model) {
        if (model != null && !model.isBlank()) {
            String sanitized = model.trim();
            log.info("Updating AI model to {}", sanitized);
            properties.setModel(sanitized);
        }
    }

    public List<String> getAvailableProviders() {
        return AiStatusDto.DEFAULT_PROVIDERS;
    }
}


