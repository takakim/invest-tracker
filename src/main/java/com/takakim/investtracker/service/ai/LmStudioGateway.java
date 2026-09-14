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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LmStudioGateway {

    private static final Logger log = LoggerFactory.getLogger(LmStudioGateway.class);

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public LmStudioGateway(AiProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        int timeout = properties != null && properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds()
                : 60;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 10)));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeout));

        String baseUrl = properties != null && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                ? properties.getBaseUrl().replaceAll("/+$", "")
                : "http://localhost:1234";

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public LmStudioGateway(AiProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public AiStatusDto checkStatus() {
        if (!properties.isEnabled()) {
            return new AiStatusDto(false, false, "LM_STUDIO", properties.getBaseUrl(), properties.getModel(), List.of(), "AI evaluation is disabled in configuration");
        }

        List<String> discoveredModels = new ArrayList<>();
        // Try native LM Studio v1 models endpoint first
        try {
            String json = restClient.get()
                    .uri("/api/v1/models")
                    .headers(this::applyAuthHeaders)
                    .retrieve()
                    .body(String.class);

            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode modelsNode = root.has("models") ? root.get("models") : root.get("data");
                if (modelsNode != null && modelsNode.isArray()) {
                    for (JsonNode m : modelsNode) {
                        if (m.has("id")) {
                            discoveredModels.add(m.get("id").asText());
                        } else if (m.has("name")) {
                            discoveredModels.add(m.get("name").asText());
                        }
                    }
                }
            }
            return new AiStatusDto(true, true, "LM_STUDIO", properties.getBaseUrl(), properties.getModel(), discoveredModels, null);
        } catch (Exception e) {
            log.debug("LM Studio /api/v1/models check failed, attempting /v1/models fallback: {}", e.getMessage());
        }

        // Try standard OpenAI compatibility endpoint
        try {
            String json = restClient.get()
                    .uri("/v1/models")
                    .headers(this::applyAuthHeaders)
                    .retrieve()
                    .body(String.class);

            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode dataNode = root.get("data");
                if (dataNode != null && dataNode.isArray()) {
                    for (JsonNode m : dataNode) {
                        if (m.has("id")) {
                            discoveredModels.add(m.get("id").asText());
                        }
                    }
                }
            }
            return new AiStatusDto(true, true, "LM_STUDIO", properties.getBaseUrl(), properties.getModel(), discoveredModels, null);
        } catch (Exception e) {
            log.warn("Failed to connect to LM Studio at {}: {}", properties.getBaseUrl(), e.getMessage());
            return new AiStatusDto(true, false, "LM_STUDIO", properties.getBaseUrl(), properties.getModel(), List.of(), "Cannot connect to LM Studio: " + e.getMessage());
        }
    }

    public String generateChatCompletion(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI features are currently disabled in configuration");
        }

        String targetModel = (properties.getModel() != null && !properties.getModel().isBlank())
                ? properties.getModel()
                : "gemma4-12b";

        // Try native LM Studio /api/v1/chat endpoint
        try {
            Map<String, Object> payload = Map.of(
                    "model", targetModel,
                    "input", List.of(
                            Map.of("type", "message", "role", "system", "content", systemPrompt),
                            Map.of("type", "message", "role", "user", "content", userPrompt)
                    ),
                    "temperature", properties.getTemperature(),
                    "max_output_tokens", properties.getMaxTokens()
            );

            String responseBody = restClient.post()
                    .uri("/api/v1/chat")
                    .headers(this::applyAuthHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            Optional<String> content = extractContentFromNativeResponse(responseBody);
            if (content.isPresent()) {
                return content.get();
            }
        } catch (Exception e) {
            log.debug("Native LM Studio /api/v1/chat request failed, attempting OpenAI /v1/chat/completions fallback: {}", e.getMessage());
        }

        // Fallback to OpenAI-compatible /v1/chat/completions
        try {
            Map<String, Object> payload = Map.of(
                    "model", targetModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", properties.getTemperature(),
                    "max_tokens", properties.getMaxTokens()
            );

            String responseBody = restClient.post()
                    .uri("/v1/chat/completions")
                    .headers(this::applyAuthHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            Optional<String> content = extractContentFromOpenAiResponse(responseBody);
            if (content.isPresent()) {
                return content.get();
            }
            throw new IllegalStateException("LM Studio returned an empty chat completion response");
        } catch (RestClientResponseException e) {
            log.error("LM Studio HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("LM Studio API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error communicating with LM Studio at {}: {}", properties.getBaseUrl(), e.getMessage());
            throw new IllegalStateException("Failed to communicate with LM Studio: " + e.getMessage(), e);
        }
    }

    private void applyAuthHeaders(HttpHeaders headers) {
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.setBearerAuth(properties.getApiKey().trim());
        }
    }

    private Optional<String> extractContentFromNativeResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode output = root.get("output");
            if (output != null && output.isArray() && !output.isEmpty()) {
                for (JsonNode item : output) {
                    if (item.has("content")) {
                        String text = item.get("content").asText();
                        if (text != null && !text.isBlank()) {
                            return Optional.of(text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse LM Studio native response: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> extractContentFromOpenAiResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    String text = message.get("content").asText();
                    if (text != null && !text.isBlank()) {
                        return Optional.of(text);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse OpenAI-compatible response: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
