package com.takakim.investtracker.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

/**
 * AI gateway for OpenAI's API (https://api.openai.com).
 * Uses the standard /v1/chat/completions and /v1/models endpoints.
 * Auth: Authorization: Bearer <OPENAI_API_KEY>
 */
@Component
public class OpenAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenAiGateway.class);
    private static final String PROVIDER_NAME = "OPENAI";

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiGateway(AiProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        int timeout = properties != null && properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds() : 60;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 10)));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeout));

        String baseUrl = (properties != null && properties.getOpenaiBaseUrl() != null
                && !properties.getOpenaiBaseUrl().isBlank())
                ? properties.getOpenaiBaseUrl().replaceAll("/+$", "")
                : "https://api.openai.com";

        this.restClient = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public OpenAiGateway(AiProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public AiStatusDto checkStatus() {
        if (!properties.isEnabled()) {
            return new AiStatusDto(false, false, PROVIDER_NAME, properties.getOpenaiBaseUrl(),
                    properties.getModel(), List.of(), "AI evaluation is disabled in configuration");
        }
        String apiKey = properties.getOpenaiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new AiStatusDto(true, false, PROVIDER_NAME, properties.getOpenaiBaseUrl(),
                    properties.getModel(), List.of(), "OPENAI_API_KEY is not configured");
        }

        List<String> discoveredModels = new ArrayList<>();
        try {
            String json = restClient.get()
                    .uri("/v1/models")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .retrieve()
                    .body(String.class);

            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode data = root.get("data");
                if (data != null && data.isArray()) {
                    for (JsonNode m : data) {
                        if (m.has("id")) discoveredModels.add(m.get("id").asText());
                    }
                }
            }
            return new AiStatusDto(true, true, PROVIDER_NAME, properties.getOpenaiBaseUrl(),
                    properties.getModel(), discoveredModels, null);
        } catch (Exception e) {
            log.warn("Failed to connect to OpenAI at {}: {}", properties.getOpenaiBaseUrl(), e.getMessage());
            return new AiStatusDto(true, false, PROVIDER_NAME, properties.getOpenaiBaseUrl(),
                    properties.getModel(), List.of(), "Cannot connect to OpenAI: " + e.getMessage());
        }
    }

    @Override
    public String generateChatCompletion(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI features are currently disabled in configuration");
        }
        String apiKey = properties.getOpenaiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }
        String targetModel = (properties.getModel() != null && !properties.getModel().isBlank())
                ? properties.getModel() : "gpt-4o-mini";

        Map<String, Object> payload = Map.of(
                "model", targetModel,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", properties.getTemperature(),
                "max_tokens", properties.getMaxTokens()
        );

        try {
            String responseBody = restClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    String text = message.get("content").asText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
            throw new IllegalStateException("OpenAI returned an empty chat completion response");
        } catch (RestClientResponseException e) {
            log.error("OpenAI HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("OpenAI API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with OpenAI: {}", e.getMessage());
            throw new IllegalStateException("Failed to communicate with OpenAI: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
