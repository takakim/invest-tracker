package com.takakim.investtracker.service.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.takakim.investtracker.config.AiProperties;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI gateway for Anthropic's Claude API (https://api.anthropic.com).
 * Uses /v1/messages endpoint.
 * Auth: x-api-key header and anthropic-version header.
 */
@Component
public class AnthropicGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AnthropicGateway.class);
    private static final String PROVIDER_NAME = "ANTHROPIC";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_MODEL = "claude-3-5-haiku-latest";

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SimpleClientHttpRequestFactory requestFactory;

    @Autowired
    public AnthropicGateway(AiProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        int timeout = properties != null && properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds() : 60;

        this.requestFactory = new SimpleClientHttpRequestFactory();
        this.requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 10)));
        this.requestFactory.setReadTimeout(Duration.ofSeconds(timeout));

        String baseUrl = (properties != null && properties.getAnthropicBaseUrl() != null
                && !properties.getAnthropicBaseUrl().isBlank())
                ? properties.getAnthropicBaseUrl().replaceAll("/+$", "")
                : "https://api.anthropic.com";

        this.restClient = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
                .baseUrl(baseUrl)
                .requestFactory(this.requestFactory)
                .build();
    }

    public AnthropicGateway(AiProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.requestFactory = null;
    }

    @Override
    public AiStatusDto checkStatus() {
        if (!properties.isEnabled()) {
            return new AiStatusDto(false, false, PROVIDER_NAME, properties.getAnthropicBaseUrl(),
                    properties.getModel(), List.of(), "AI evaluation is disabled in configuration", getTimeoutSeconds());
        }
        String apiKey = properties.getAnthropicApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new AiStatusDto(true, false, PROVIDER_NAME, properties.getAnthropicBaseUrl(),
                    properties.getModel(), List.of(), "ANTHROPIC_API_KEY is not configured", getTimeoutSeconds());
        }

        // Anthropic has no public /models endpoint that accepts no body;
        // we perform a lightweight probe via a minimal /v1/messages request.
        // A 400 (invalid request but reachable) still counts as "connected".
        String activeModel = resolveActiveModel();
        try {
            Map<String, Object> probePayload = Map.of(
                    "model", activeModel,
                    "max_tokens", 1,
                    "messages", List.of(Map.of("role", "user", "content", "ping"))
            );
            restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey.trim())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(probePayload)
                    .retrieve()
                    .body(String.class);
            return new AiStatusDto(true, true, PROVIDER_NAME, properties.getAnthropicBaseUrl(),
                    activeModel, List.of(activeModel), null, getTimeoutSeconds());
        } catch (RestClientResponseException e) {
            // 4xx means we reached Anthropic but the request was rejected (e.g. invalid model) — still "connected"
            if (e.getStatusCode().is4xxClientError()) {
                return new AiStatusDto(true, true, PROVIDER_NAME, properties.getAnthropicBaseUrl(),
                        activeModel, List.of(activeModel), null, getTimeoutSeconds());
            }
            log.warn("Failed to connect to Anthropic API: {}", e.getMessage());
            return new AiStatusDto(true, false, PROVIDER_NAME, properties.getAnthropicBaseUrl(),
                    activeModel, List.of(), "Cannot connect to Anthropic: " + e.getMessage(), getTimeoutSeconds());
        } catch (Exception e) {
            log.warn("Failed to connect to Anthropic API: {}", e.getMessage());
            return new AiStatusDto(true, false, PROVIDER_NAME, properties.getAnthropicBaseUrl(),
                    activeModel, List.of(), "Cannot connect to Anthropic: " + e.getMessage(), getTimeoutSeconds());
        }
    }

    public String resolveActiveModel() {
        String configured = properties.getModel();
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured.trim())) {
            return configured.trim();
        }
        return DEFAULT_MODEL;
    }

    @Override
    public String generateChatCompletion(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI features are currently disabled in configuration");
        }
        String apiKey = properties.getAnthropicApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not configured");
        }
        String targetModel = resolveActiveModel();

        Map<String, Object> payload = Map.of(
                "model", targetModel,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)),
                "max_tokens", properties.getMaxTokens(),
                "temperature", properties.getTemperature()
        );

        try {
            String responseBody = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey.trim())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            return extractAnthropicContent(responseBody);
        } catch (RestClientResponseException e) {
            log.error("Anthropic HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Anthropic API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with Anthropic: {}", e.getMessage());
            throw new IllegalStateException("Failed to communicate with Anthropic: " + e.getMessage(), e);
        }
    }

    private String extractAnthropicContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Anthropic returned an empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.get("content");
            if (content != null && content.isArray() && !content.isEmpty()) {
                JsonNode first = content.get(0);
                if (first.has("text")) {
                    String text = first.get("text").asText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse Anthropic response: {}", e.getMessage());
        }
        throw new IllegalStateException("Anthropic returned an empty or unparseable completion response");
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public void setTimeoutSeconds(int timeoutSeconds) {
        if (requestFactory != null) {
            requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        }
    }

    @Override
    public int getTimeoutSeconds() {
        return properties != null ? properties.getTimeoutSeconds() : 60;
    }
}

