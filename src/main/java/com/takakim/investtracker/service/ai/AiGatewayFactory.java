package com.takakim.investtracker.service.ai;

import com.takakim.investtracker.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Selects the active {@link AiGateway} implementation based on the configured
 * {@code AI_PROVIDER} environment variable (default: LM_STUDIO).
 *
 * <p>Supported values: {@code LM_STUDIO}, {@code OPENAI}, {@code GEMINI}, {@code ANTHROPIC}.
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
     */
    public AiGateway getActiveGateway() {
        String provider = properties.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = "LM_STUDIO";
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
            default -> {
                log.debug("Using LM Studio gateway (provider={})", provider);
                yield lmStudioGateway;
            }
        };
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
}


