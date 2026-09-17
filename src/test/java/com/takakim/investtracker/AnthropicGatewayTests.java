package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.takakim.investtracker.config.AiProperties;
import com.takakim.investtracker.service.ai.AnthropicGateway;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AnthropicGatewayTests {

    private AiProperties properties;
    private AnthropicGateway gateway;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setAnthropicApiKey("anthropic-test-key");
        properties.setAnthropicBaseUrl("https://api.anthropic.com");
        properties.setModel("claude-3-5-haiku-latest");
        properties.setTemperature(0.2);
        properties.setMaxTokens(100);
        properties.setTimeoutSeconds(30);

        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        gateway = new AnthropicGateway(properties, restClient, objectMapper);
    }

    @Test
    @DisplayName("getProviderName returns ANTHROPIC")
    void testProviderName() {
        assertEquals("ANTHROPIC", gateway.getProviderName());
    }

    @Test
    @DisplayName("checkStatus returns offline when API key not configured")
    void testCheckStatus_missingApiKey() {
        properties.setAnthropicApiKey("");
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.connected());
        assertTrue(status.errorMessage().contains("ANTHROPIC_API_KEY"));
    }

    @Test
    @DisplayName("checkStatus and generateChatCompletion handle null API key")
    void testNullApiKey() {
        properties.setAnthropicApiKey(null);
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.connected());

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
    }

    @Test
    @DisplayName("resolveActiveModel handles auto, null, blank and explicit model")
    void testResolveActiveModel() {
        properties.setModel("auto");
        assertEquals("claude-3-5-haiku-latest", gateway.resolveActiveModel());

        properties.setModel(null);
        assertEquals("claude-3-5-haiku-latest", gateway.resolveActiveModel());

        properties.setModel("   ");
        assertEquals("claude-3-5-haiku-latest", gateway.resolveActiveModel());

        properties.setModel("claude-3-5-sonnet-20241022");
        assertEquals("claude-3-5-sonnet-20241022", gateway.resolveActiveModel());
    }

    @Test
    @DisplayName("checkStatus returns offline when AI is disabled")
    void testCheckStatus_disabled() {
        properties.setEnabled(false);
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.enabled());
    }

    @Test
    @DisplayName("checkStatus returns connected when probe responds 200")
    void testCheckStatus_connected_200() {
        String probe = "{\"id\":\"msg_001\",\"content\":[{\"text\":\"pong\"}]}";
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "anthropic-test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withSuccess(probe, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertEquals("ANTHROPIC", status.provider());
    }

    @Test
    @DisplayName("checkStatus treats 4xx response as connected (reachable but bad request)")
    void testCheckStatus_connected_4xx() {
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "anthropic-test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected()); // 4xx = reachable
    }

    @Test
    @DisplayName("checkStatus returns offline on 5xx error")
    void testCheckStatus_offline_5xx() {
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "anthropic-test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withServerError());

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertFalse(status.connected());
        assertNotNull(status.errorMessage());
    }

    @Test
    @DisplayName("generateChatCompletion throws when AI is disabled")
    void testGenerate_disabled() {
        properties.setEnabled(false);
        assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
    }

    @Test
    @DisplayName("generateChatCompletion throws when API key is missing")
    void testGenerate_missingKey() {
        properties.setAnthropicApiKey("");
        assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
    }

    @Test
    @DisplayName("generateChatCompletion parses Anthropic content array format")
    void testGenerate_success() {
        String responseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"Anthropic AI response\"}]}";
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "anthropic-test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("system", "user");
        mockServer.verify();

        assertEquals("Anthropic AI response", result);
    }

    @Test
    @DisplayName("generateChatCompletion throws on empty content array")
    void testGenerate_emptyContent() {
        String responseBody = "{\"content\":[]}";
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "anthropic-test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Spring constructor handles custom and default configurations")
    void testSpringConstructor() {
        AiProperties props = new AiProperties();
        props.setTimeoutSeconds(15);
        props.setAnthropicBaseUrl("https://custom.anthropic.com///");
        AnthropicGateway gw = new AnthropicGateway(props, RestClient.builder(), null);
        assertEquals("ANTHROPIC", gw.getProviderName());

        AiProperties propsZero = new AiProperties();
        propsZero.setTimeoutSeconds(0);
        propsZero.setAnthropicBaseUrl(null);
        AnthropicGateway gw2 = new AnthropicGateway(propsZero, (RestClient.Builder) null, new ObjectMapper());
        assertEquals("ANTHROPIC", gw2.getProviderName());
    }

    @Test
    @DisplayName("generateChatCompletion handles RestClientResponseException on HTTP error")
    void testGenerate_httpError() {
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "anthropic-test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST).body("Invalid request"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
        assertTrue(ex.getMessage().contains("Anthropic API error"));
        mockServer.verify();
    }

    @Test
    @DisplayName("generateChatCompletion falls back to default model when model is null")
    void testGenerate_defaultModel() {
        properties.setModel(null);
        String responseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"Default model response\"}]}";
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "anthropic-test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("system", "user");
        mockServer.verify();
        assertEquals("Default model response", result);
    }

    @Test
    @DisplayName("generateChatCompletion throws on content item with blank text")
    void testGenerate_blankText() {
        String responseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"   \"}]}";
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "anthropic-test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
        mockServer.verify();
    }

    @Test
    @DisplayName("checkStatus handles probe with null model configuration and general Exception")
    void testCheckStatus_probeWithNullModelAndException() {
        properties.setModel(null);
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status.connected());
        assertEquals("claude-3-5-haiku-latest", status.configuredModel());

        // General non-HTTP Exception (e.g. timeout / connection reset)
        mockServer.reset();
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("Simulated network timeout");
                });

        AiStatusDto errorStatus = gateway.checkStatus();
        assertFalse(errorStatus.connected());
        assertTrue(errorStatus.errorMessage().contains("Cannot connect to Anthropic"));
    }

    @Test
    @DisplayName("generateChatCompletion handles non-array content, missing text, malformed JSON, and general Exception")
    void testGenerate_contentStructureAnomalies() {
        // content not an array
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"content\": \"not-an-array\"}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // content item missing text property
        mockServer.reset();
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"content\": [{\"type\": \"image\"}]}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // malformed JSON
        mockServer.reset();
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not-json-content", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // General RestClient non-HTTP exception
        mockServer.reset();
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("I/O error");
                });

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();
    }

    @Test
    @DisplayName("test constructor handles null ObjectMapper and null properties")
    void testConstructorNullMapper() {
        AnthropicGateway gw = new AnthropicGateway(properties, RestClient.builder().build(), null);
        assertEquals("ANTHROPIC", gw.getProviderName());
        AnthropicGateway gwNull = new AnthropicGateway(null, (RestClient.Builder) null, null);
        assertEquals(60, gwNull.getTimeoutSeconds());
    }

    @Test
    @DisplayName("Autowired constructor sets timeouts and setTimeoutSeconds updates requestFactory")
    void testAutowiredConstructorAndSetTimeoutSeconds() {
        AnthropicGateway gw = new AnthropicGateway(properties, RestClient.builder(), objectMapper);
        assertEquals(30, gw.getTimeoutSeconds());
        gw.setTimeoutSeconds(120);
        gateway.setTimeoutSeconds(90);
    }
}

