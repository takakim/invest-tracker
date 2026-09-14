package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import tools.jackson.databind.ObjectMapper;
import com.takakim.investtracker.config.AiProperties;
import com.takakim.investtracker.service.ai.LmStudioGateway;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LmStudioGatewayTests {

    private AiProperties properties;
    private LmStudioGateway gateway;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:1234");
        properties.setModel("gemma4-12b");
        properties.setTemperature(0.2);
        properties.setMaxTokens(2048);
        properties.setTimeoutSeconds(30);

        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:1234");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        gateway = new LmStudioGateway(properties, restClient, objectMapper);
    }

    @Test
    @DisplayName("checkStatus returns disabled when properties.enabled is false")
    void testCheckStatusDisabled() {
        properties.setEnabled(false);
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.enabled());
        assertFalse(status.connected());
        assertEquals("AI evaluation is disabled in configuration", status.errorMessage());
    }

    @Test
    @DisplayName("checkStatus succeeds with native /api/v1/models response")
    void testCheckStatusNativeModels() {
        String json = """
                {
                  "models": [
                    { "id": "gemma4-12b", "name": "Gemma 4 12B", "state": "loaded" },
                    { "id": "llama-3-8b", "name": "Llama 3 8B" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.enabled());
        assertTrue(status.connected());
        assertEquals("LM_STUDIO", status.provider());
        assertEquals(2, status.availableModels().size());
        assertTrue(status.availableModels().contains("gemma4-12b"));
        assertTrue(status.availableModels().contains("llama-3-8b"));
        assertNull(status.errorMessage());
    }

    @Test
    @DisplayName("checkStatus succeeds with data array in /api/v1/models")
    void testCheckStatusDataArray() {
        String json = """
                {
                  "data": [
                    { "id": "gemma4-12b" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertEquals(1, status.availableModels().size());
        assertEquals("gemma4-12b", status.availableModels().get(0));
    }

    @Test
    @DisplayName("checkStatus falls back to /v1/models when /api/v1/models fails")
    void testCheckStatusFallback() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        String openAiJson = """
                {
                  "data": [
                    { "id": "gemma4-12b" },
                    { "id": "deepseek-r1" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(openAiJson, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertEquals(2, status.availableModels().size());
    }

    @Test
    @DisplayName("checkStatus returns not connected when both endpoints fail")
    void testCheckStatusBothFail() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo("http://localhost:1234/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertFalse(status.connected());
        assertNotNull(status.errorMessage());
    }

    @Test
    @DisplayName("generateChatCompletion throws when disabled")
    void testGenerateChatCompletionDisabled() {
        properties.setEnabled(false);
        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "user"));
    }

    @Test
    @DisplayName("generateChatCompletion succeeds via native /api/v1/chat")
    void testGenerateChatCompletionNative() {
        String responseJson = """
                {
                  "model_instance_id": "gemma4-12b",
                  "output": [
                    {
                      "type": "message",
                      "content": "{\\"stance\\": \\"HOLD\\", \\"riskScore\\": 5}"
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("System instructions", "Evaluate AAPL");
        mockServer.verify();

        assertNotNull(result);
        assertTrue(result.contains("HOLD"));
    }

    @Test
    @DisplayName("generateChatCompletion falls back to /v1/chat/completions on native error")
    void testGenerateChatCompletionFallback() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        String openAiResponse = """
                {
                  "id": "chatcmpl-123",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"stance\\": \\"ACCUMULATE\\"}"
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(openAiResponse, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("System instructions", "Evaluate NVDA");
        mockServer.verify();

        assertNotNull(result);
        assertTrue(result.contains("ACCUMULATE"));
    }

    @Test
    @DisplayName("generateChatCompletion applies Bearer token when apiKey is set")
    void testGenerateChatCompletionAuthHeader() {
        properties.setApiKey("test-token-123");

        String responseJson = """
                {
                  "output": [
                    { "type": "message", "content": "Analysis complete" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token-123"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("Sys", "User");
        mockServer.verify();

        assertEquals("Analysis complete", result);
    }

    @Test
    @DisplayName("generateChatCompletion throws when both native and fallback endpoints fail")
    void testGenerateChatCompletionAllFail() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo("http://localhost:1234/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("Sys", "User"));
        mockServer.verify();
    }

    @Test
    @DisplayName("checkStatus handles model with name only")
    void testCheckStatusModelWithNameOnly() {
        String json = """
                {
                  "models": [
                    { "name": "custom-model" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertEquals(1, status.availableModels().size());
        assertEquals("custom-model", status.availableModels().get(0));
    }

    @Test
    @DisplayName("generateChatCompletion uses default model when property is null or blank")
    void testGenerateChatCompletionModelNull() {
        properties.setModel(null);

        String responseJson = """
                {
                  "output": [
                    { "type": "message", "content": "Default model used" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("Sys", "User");
        mockServer.verify();
        assertEquals("Default model used", result);
    }

    @Test
    @DisplayName("generateChatCompletion handles blank items and falls back")
    void testGenerateChatCompletionBlankItems() {
        String nativeJson = """
                {
                  "output": [
                    { "type": "message", "content": "  " },
                    { "type": "other" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(nativeJson, MediaType.APPLICATION_JSON));

        String openAiResponse = """
                {
                  "choices": [
                    {
                      "message": { "content": "OpenAI content" }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(openAiResponse, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("Sys", "User");
        mockServer.verify();
        assertEquals("OpenAI content", result);
    }

    @Test
    @DisplayName("constructor handles null and blank properties")
    void testConstructorNullProperties() {
        AiProperties props = new AiProperties();
        props.setBaseUrl(null);
        props.setTimeoutSeconds(0);
        LmStudioGateway gw = new LmStudioGateway(props, (RestClient.Builder) null, (ObjectMapper) null);
        assertNotNull(gw);

        props.setBaseUrl("   ");
        LmStudioGateway gw2 = new LmStudioGateway(props, (RestClient.Builder) null, (ObjectMapper) null);
        assertNotNull(gw2);
    }

    @Test
    @DisplayName("checkStatus fallback to /v1/models when /api/v1/models returns 404")
    void testCheckStatusFallbackOpenAi() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        String openAiModelsJson = """
                {
                  "data": [
                    { "id": "gemma4-fallback" },
                    { "description": "no-id" }
                  ]
                }
                """;
        mockServer.expect(requestTo("http://localhost:1234/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(openAiModelsJson, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertEquals(1, status.availableModels().size());
        assertEquals("gemma4-fallback", status.availableModels().get(0));
    }

    @Test
    @DisplayName("checkStatus returns error when both endpoints fail with service unavailable")
    void testCheckStatusBothEndpointsFailWithServiceUnavailable() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        mockServer.expect(requestTo("http://localhost:1234/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertFalse(status.connected());
        assertNotNull(status.errorMessage());
    }

    @Test
    @DisplayName("checkStatus handles models without id or name")
    void testCheckStatusModelsNoIdNoName() {
        String json = """
                {
                  "models": [
                    { "state": "loaded" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertTrue(status.availableModels().isEmpty());
    }

    @Test
    @DisplayName("generateChatCompletion throws when OpenAI choices are empty or invalid")
    void testGenerateChatCompletionEmptyChoices() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        String emptyChoicesJson = """
                {
                  "choices": []
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(emptyChoicesJson, MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                gateway.generateChatCompletion("Sys", "User")
        );
        assertTrue(ex.getMessage().contains("empty chat completion response"));
        mockServer.verify();
    }

    @Test
    @DisplayName("generateChatCompletion with apiKey sets Authorization header")
    void testGenerateChatCompletionWithApiKey() {
        properties.setApiKey("lm-studio-token-123");

        String responseJson = """
                {
                  "output": [
                    { "content": "Authenticated output" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer lm-studio-token-123"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("Sys", "User");
        mockServer.verify();
        assertEquals("Authenticated output", result);
    }

    @Test
    @DisplayName("generateChatCompletion handles malformed JSON in native and OpenAI responses")
    void testGenerateChatCompletionMalformedJson() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not-valid-json-native", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("http://localhost:1234/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not-valid-json-openai", MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                gateway.generateChatCompletion("Sys", "User")
        );
        assertTrue(ex.getMessage().contains("empty chat completion response"));
        mockServer.verify();
    }

    @Test
    @DisplayName("generateChatCompletion handles non-array or missing message in OpenAI fallback")
    void testGenerateChatCompletionOpenAiMissingMessage() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"output\": \"not-an-array\"}", MediaType.APPLICATION_JSON));

        String openAiNoMessage = """
                {
                  "choices": [
                    { "other": "value" },
                    { "message": { "no_content": 123 } },
                    { "message": { "content": "   " } }
                  ]
                }
                """;
        mockServer.expect(requestTo("http://localhost:1234/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(openAiNoMessage, MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                gateway.generateChatCompletion("Sys", "User")
        );
        assertTrue(ex.getMessage().contains("empty chat completion response"));
        mockServer.verify();
    }

    @Test
    @DisplayName("generateChatCompletion with blank apiKey does not set auth header")
    void testGenerateChatCompletionWithBlankApiKey() {
        properties.setApiKey("   ");

        String responseJson = """
                {
                  "output": [
                    { "content": "Unauthenticated output" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("Sys", "User");
        mockServer.verify();
        assertEquals("Unauthenticated output", result);
    }

    @Test
    @DisplayName("checkStatus handles non-array models and non-array data")
    void testCheckStatusNonArrayModelsAndData() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\": \"not-array\"}", MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertTrue(status.availableModels().isEmpty());
    }

    @Test
    @DisplayName("checkStatus fallback handles null or non-array data node")
    void testCheckStatusFallbackNullOrNonArrayData() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockServer.expect(requestTo("http://localhost:1234/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\": \"not-an-array\"}", MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertTrue(status.availableModels().isEmpty());
    }

    @Test
    @DisplayName("generateChatCompletion handles blank response bodies from endpoints")
    void testGenerateChatCompletionBlankResponseBodies() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("   ", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("http://localhost:1234/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                gateway.generateChatCompletion("Sys", "User")
        );
        assertTrue(ex.getMessage().contains("empty chat completion response"));
        mockServer.verify();
    }

    @Test
    @DisplayName("generateChatCompletion native skips blank items until valid content found")
    void testGenerateChatCompletionNativeSkipsBlankItems() {
        String responseJson = """
                {
                  "output": [
                    { "type": "message", "content": "" },
                    { "type": "message", "content": "   " },
                    { "type": "message", "content": "{\\"stance\\": \\"BUY\\"}" }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("Sys", "User");
        mockServer.verify();

        assertNotNull(result);
        assertTrue(result.contains("BUY"));
    }

    @Test
    @DisplayName("generateChatCompletion OpenAI fallback throws when message content is blank")
    void testGenerateChatCompletionOpenAiBlankMessageContent() {
        mockServer.expect(requestTo("http://localhost:1234/api/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        String openAiResponse = """
                {
                  "choices": [
                    {
                      "message": { "role": "assistant", "content": "   " }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:1234/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(openAiResponse, MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                gateway.generateChatCompletion("Sys", "User")
        );
        assertTrue(ex.getMessage().contains("empty chat completion response"));
        mockServer.verify();
    }

    @Test
    @DisplayName("checkStatus applies Bearer authorization when apiKey is configured")
    void testCheckStatusWithApiKey() {
        properties.setApiKey("my-secret-key");

        String json = "{\"models\": []}";
        mockServer.expect(requestTo("http://localhost:1234/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer my-secret-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status.connected());
    }
}

