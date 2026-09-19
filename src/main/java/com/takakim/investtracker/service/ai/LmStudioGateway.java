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
public class LmStudioGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(LmStudioGateway.class);

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SimpleClientHttpRequestFactory requestFactory;

    @Autowired
    public LmStudioGateway(AiProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        int timeout = properties != null && properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds()
                : 60;

        this.requestFactory = new SimpleClientHttpRequestFactory();
        this.requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 10)));
        this.requestFactory.setReadTimeout(Duration.ofSeconds(timeout));

        String baseUrl = properties != null && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                ? properties.getBaseUrl().replaceAll("/+$", "")
                : "http://localhost:1234";

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(this.requestFactory)
                .build();
    }

    public LmStudioGateway(AiProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.requestFactory = null;
    }


    public record DiscoveredModelsResult(
            List<String> loadedModelIds,
            List<String> availableLlmModelIds,
            List<String> allModelIds
    ) {}

    private static final List<String> EMBEDDING_KEYWORDS = List.of(
            "embed", "nomic", "bge", "minilm", "sentence-transformers", "embedding"
    );

    private boolean isEmbeddingModel(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String kw : EMBEDDING_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private volatile String cachedActiveModel;

    public String resolveActiveModel() {
        if (properties.getModel() != null && !properties.getModel().isBlank()
                && !"auto".equalsIgnoreCase(properties.getModel().trim())
                && !"default".equalsIgnoreCase(properties.getModel().trim())) {
            return properties.getModel().trim();
        }
        if (cachedActiveModel != null && !cachedActiveModel.isBlank()) {
            return cachedActiveModel;
        }
        return "google/gemma-4-e4b";
    }

    public String resolveActiveModelFrom(DiscoveredModelsResult models) {
        String configured = properties.getModel();
        boolean isAutoOrEmpty = configured == null || configured.isBlank()
                || "auto".equalsIgnoreCase(configured.trim())
                || "default".equalsIgnoreCase(configured.trim());

        if (models != null) {
            // 1. If explicit configured model exists in discovered models, respect user's choice
            if (!isAutoOrEmpty && models.allModelIds().contains(configured.trim())) {
                return configured.trim();
            }

            // 2. If any model is currently loaded into RAM/VRAM, prioritize it
            if (!models.loadedModelIds().isEmpty()) {
                log.info("Auto-selected actively loaded local model: {}", models.loadedModelIds().get(0));
                return models.loadedModelIds().get(0);
            }

            // 3. Pick the first available local LLM
            if (!models.availableLlmModelIds().isEmpty()) {
                String firstLlm = models.availableLlmModelIds().get(0);
                log.info("Auto-selected first available local LLM: {}", firstLlm);
                return firstLlm;
            }

            // 4. Any discovered model
            if (!models.allModelIds().isEmpty()) {
                return models.allModelIds().get(0);
            }
        }

        // 5. Fallback
        return (!isAutoOrEmpty) ? configured.trim() : "google/gemma-4-e4b";
    }

    @Override
    public AiStatusDto checkStatus() {
        if (!properties.isEnabled()) {
            return new AiStatusDto(false, false, "LM_STUDIO", properties.getBaseUrl(), properties.getModel(), List.of(), "AI evaluation is disabled in configuration", getTimeoutSeconds());
        }

        List<String> discoveredModels = new ArrayList<>();
        List<String> loadedModels = new ArrayList<>();
        List<String> availableLlms = new ArrayList<>();

        // Try native LM Studio v1 models endpoint first
        try {
            String json = restClient.get()
                    .uri("/api/v1/models")
                    .headers(this::applyAuthHeaders)
                    .retrieve()
                    .body(String.class);

            if (json != null) {
                parseNativeModelsJson(json, discoveredModels, loadedModels, availableLlms);
                DiscoveredModelsResult result = new DiscoveredModelsResult(loadedModels, availableLlms, discoveredModels);
                String activeModel = resolveActiveModelFrom(result);
                this.cachedActiveModel = activeModel;
                return new AiStatusDto(true, true, "LM_STUDIO", properties.getBaseUrl(), activeModel, discoveredModels, null, getTimeoutSeconds());
            }
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
                parseOpenAiModelsJson(json, discoveredModels, availableLlms);
                DiscoveredModelsResult result = new DiscoveredModelsResult(loadedModels, availableLlms, discoveredModels);
                String activeModel = resolveActiveModelFrom(result);
                this.cachedActiveModel = activeModel;
                return new AiStatusDto(true, true, "LM_STUDIO", properties.getBaseUrl(), activeModel, discoveredModels, null, getTimeoutSeconds());
            }
        } catch (Exception e) {
            log.warn("Failed to connect to LM Studio at {}: {}", properties.getBaseUrl(), e.getMessage());
            return new AiStatusDto(true, false, "LM_STUDIO", properties.getBaseUrl(), properties.getModel(), List.of(), "Cannot connect to LM Studio: " + e.getMessage(), getTimeoutSeconds());
        }

        return new AiStatusDto(true, false, "LM_STUDIO", properties.getBaseUrl(), properties.getModel(), List.of(), "Cannot connect to LM Studio", getTimeoutSeconds());
    }

    private void parseNativeModelsJson(String json, List<String> discoveredModels, List<String> loadedModels, List<String> availableLlms) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode modelsNode = root.has("models") ? root.get("models") : root.get("data");
            if (modelsNode != null && modelsNode.isArray()) {
                for (JsonNode m : modelsNode) {
                    String modelId = null;
                    if (m.has("id")) {
                        modelId = m.get("id").asText();
                    } else if (m.has("key")) {
                        modelId = m.get("key").asText();
                    } else if (m.has("name")) {
                        modelId = m.get("name").asText();
                    } else if (m.has("display_name")) {
                        modelId = m.get("display_name").asText();
                    }

                    if (modelId != null && !discoveredModels.contains(modelId)) {
                        discoveredModels.add(modelId);
                    }

                    boolean isEmbedding = (m.has("type") && "embedding".equalsIgnoreCase(m.get("type").asText()))
                            || isEmbeddingModel(modelId);

                    if (!isEmbedding && modelId != null && !availableLlms.contains(modelId)) {
                        availableLlms.add(modelId);
                    }

                    if (m.has("loaded_instances") && m.get("loaded_instances").isArray() && !m.get("loaded_instances").isEmpty()) {
                        for (JsonNode inst : m.get("loaded_instances")) {
                            String instId = inst.has("id") ? inst.get("id").asText() : modelId;
                            if (instId != null && !loadedModels.contains(instId)) {
                                loadedModels.add(instId);
                            }
                            if (instId != null && !discoveredModels.contains(instId)) {
                                discoveredModels.add(instId);
                            }
                            if (!isEmbedding && instId != null && !availableLlms.contains(instId)) {
                                availableLlms.add(instId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing native models JSON: {}", e.getMessage());
        }
    }

    private void parseOpenAiModelsJson(String json, List<String> discoveredModels, List<String> availableLlms) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode m : dataNode) {
                    if (m.has("id")) {
                        String modelId = m.get("id").asText();
                        if (!discoveredModels.contains(modelId)) {
                            discoveredModels.add(modelId);
                        }
                        if (!isEmbeddingModel(modelId) && !availableLlms.contains(modelId)) {
                            availableLlms.add(modelId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing OpenAI models JSON: {}", e.getMessage());
        }
    }

    @Override
    public String generateChatCompletion(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI features are currently disabled in configuration");
        }

        String targetModel = resolveActiveModel();
        try {
            return executeChatCompletion(targetModel, systemPrompt, userPrompt);
        } catch (RestClientResponseException e) {
            log.error("LM Studio HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("LM Studio API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error communicating with LM Studio at {}: {}", properties.getBaseUrl(), e.getMessage());
            throw new IllegalStateException("Failed to communicate with LM Studio: " + e.getMessage(), e);
        }
    }


    private String executeChatCompletion(String targetModel, String systemPrompt, String userPrompt) {
        // Try native LM Studio /api/v1/chat endpoint first
        try {
            Map<String, Object> nativePayload = new java.util.LinkedHashMap<>();
            nativePayload.put("model", targetModel);
            nativePayload.put("input", List.of(
                    Map.of("type", "message", "role", "system", "content", systemPrompt),
                    Map.of("type", "message", "role", "user", "content", userPrompt)
            ));
            nativePayload.put("temperature", properties.getTemperature());
            nativePayload.put("max_output_tokens", properties.getMaxTokens());
            if (properties.getReasoningEffort() != null && !properties.getReasoningEffort().isBlank()) {
                nativePayload.put("reasoning_effort", properties.getReasoningEffort().trim());
            }

            String responseBody = restClient.post()
                    .uri("/api/v1/chat")
                    .headers(this::applyAuthHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(nativePayload)
                    .retrieve()
                    .body(String.class);

            Optional<String> content = extractContentFromNativeResponse(responseBody);
            if (content.isPresent()) {
                return content.get();
            }
        } catch (Exception e) {
            log.debug("Native LM Studio /api/v1/chat request failed, attempting OpenAI /v1/chat/completions fallback: {}", e.getMessage());
        }

        // Fallback to OpenAI-compatible /v1/chat/completions (universally supported by LM Studio)
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", targetModel);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("temperature", properties.getTemperature());
        payload.put("max_tokens", properties.getMaxTokens());
        if (properties.getReasoningEffort() != null && !properties.getReasoningEffort().isBlank()) {
            payload.put("reasoning_effort", properties.getReasoningEffort().trim());
        }

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
    }

    public Optional<String> extractContentFromNativeResponse(String responseBody) {
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

    private void applyAuthHeaders(HttpHeaders headers) {
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.setBearerAuth(properties.getApiKey().trim());
        }
    }

    public Optional<String> extractContentFromOpenAiResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode choice0 = choices.get(0);
                JsonNode message = choice0.get("message");
                if (message != null) {
                    if (message.has("content")) {
                        String text = message.get("content").asText();
                        if (text != null && !text.isBlank()) {
                            return Optional.of(text);
                        }
                    }
                    if (message.has("reasoning_content")) {
                        String reasoning = message.get("reasoning_content").asText();
                        if (reasoning != null && !reasoning.isBlank()) {
                            Optional<String> jsonCandidate = extractJsonCandidate(reasoning);
                            if (jsonCandidate.isPresent()) {
                                return jsonCandidate;
                            }
                        }
                    }
                }
                if (choice0.has("text")) {
                    String text = choice0.get("text").asText();
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

    public Optional<String> extractJsonCandidate(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        int codeFence = text.indexOf("```json");
        if (codeFence >= 0) {
            int start = codeFence + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                String candidate = text.substring(start, end).trim();
                if (isValidJson(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String candidate = text.substring(firstBrace, lastBrace + 1).trim();
            if (isValidJson(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private boolean isValidJson(String candidate) {
        try {
            objectMapper.readTree(candidate);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }


    @Override
    public String getProviderName() {
        return "LM_STUDIO";
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

