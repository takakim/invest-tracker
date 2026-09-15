package com.takakim.investtracker.service.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.takakim.investtracker.config.AiProperties;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * AI gateway for Google Gemini API (https://generativelanguage.googleapis.com).
 * Uses /v1beta/models/{model}:generateContent with query param ?key=API_KEY.
 */
@Component
public class GeminiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(GeminiGateway.class);
    private static final String PROVIDER_NAME = "GEMINI";
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiGateway(AiProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        int timeout = properties != null && properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds() : 60;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 10)));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeout));

        String baseUrl = (properties != null && properties.getGeminiBaseUrl() != null
                && !properties.getGeminiBaseUrl().isBlank())
                ? properties.getGeminiBaseUrl().replaceAll("/+$", "")
                : "https://generativelanguage.googleapis.com";

        this.restClient = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public GeminiGateway(AiProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public AiStatusDto checkStatus() {
        if (!properties.isEnabled()) {
            return new AiStatusDto(false, false, PROVIDER_NAME, properties.getGeminiBaseUrl(),
                    properties.getModel(), List.of(), "AI evaluation is disabled in configuration");
        }
        String apiKey = properties.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new AiStatusDto(true, false, PROVIDER_NAME, properties.getGeminiBaseUrl(),
                    properties.getModel(), List.of(), "GEMINI_API_KEY is not configured");
        }

        List<String> discoveredModels = new ArrayList<>();
        try {
            String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1beta/models")
                            .queryParam("key", apiKey.trim())
                            .build())
                    .retrieve()
                    .body(String.class);

            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode models = root.get("models");
                if (models != null && models.isArray()) {
                    for (JsonNode m : models) {
                        if (m.has("name")) {
                            String name = m.get("name").asText();
                            // Strip "models/" prefix for cleaner display
                            discoveredModels.add(name.startsWith("models/") ? name.substring(7) : name);
                        }
                    }
                }
            }
            return new AiStatusDto(true, true, PROVIDER_NAME, properties.getGeminiBaseUrl(),
                    properties.getModel(), discoveredModels, null);
        } catch (Exception e) {
            log.warn("Failed to connect to Gemini API: {}", e.getMessage());
            return new AiStatusDto(true, false, PROVIDER_NAME, properties.getGeminiBaseUrl(),
                    properties.getModel(), List.of(), "Cannot connect to Gemini: " + e.getMessage());
        }
    }

    @Override
    public String generateChatCompletion(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI features are currently disabled in configuration");
        }
        String apiKey = properties.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }
        String targetModel = (properties.getModel() != null && !properties.getModel().isBlank())
                ? properties.getModel() : DEFAULT_MODEL;

        // Gemini uses system_instruction + user contents
        Map<String, Object> payload = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", properties.getTemperature(),
                        "maxOutputTokens", properties.getMaxTokens()
                )
        );

        try {
            String responseBody = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey.trim())
                            .build(targetModel))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            return extractGeminiContent(responseBody);
        } catch (RestClientResponseException e) {
            log.error("Gemini HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Gemini API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with Gemini: {}", e.getMessage());
            throw new IllegalStateException("Failed to communicate with Gemini: " + e.getMessage(), e);
        }
    }

    private String extractGeminiContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.get("candidates");
            if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
                JsonNode content = candidates.get(0).get("content");
                if (content != null) {
                    JsonNode parts = content.get("parts");
                    if (parts != null && parts.isArray() && !parts.isEmpty()) {
                        String text = parts.get(0).get("text").asText();
                        if (text != null && !text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse Gemini response: {}", e.getMessage());
        }
        throw new IllegalStateException("Gemini returned an empty or unparseable completion response");
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
