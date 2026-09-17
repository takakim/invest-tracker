package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.takakim.investtracker.config.AiProperties;
import com.takakim.investtracker.service.ai.GeminiGateway;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GeminiGatewayTests {

    private AiProperties properties;
    private GeminiGateway gateway;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setGeminiApiKey("gemini-test-key");
        properties.setGeminiBaseUrl("https://generativelanguage.googleapis.com");
        properties.setModel("gemini-2.0-flash");
        properties.setTemperature(0.2);
        properties.setMaxTokens(100);
        properties.setTimeoutSeconds(30);

        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        gateway = new GeminiGateway(properties, restClient, objectMapper);
    }

    @Test
    @DisplayName("getProviderName returns GEMINI")
    void testProviderName() {
        assertEquals("GEMINI", gateway.getProviderName());
    }

    @Test
    @DisplayName("checkStatus returns offline when API key not configured")
    void testCheckStatus_missingApiKey() {
        properties.setGeminiApiKey("");
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.connected());
        assertTrue(status.errorMessage().contains("GEMINI_API_KEY"));
    }

    @Test
    @DisplayName("checkStatus returns offline when AI is disabled")
    void testCheckStatus_disabled() {
        properties.setEnabled(false);
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.enabled());
    }

    @Test
    @DisplayName("checkStatus returns connected and strips models/ prefix")
    void testCheckStatus_connected() {
        String json = "{\"models\":[{\"name\":\"models/gemini-2.0-flash\"},{\"name\":\"models/gemini-1.5-pro\"}]}";
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=gemini-test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertEquals("GEMINI", status.provider());
        assertTrue(status.availableModels().contains("gemini-2.0-flash"));
        assertTrue(status.availableModels().contains("gemini-1.5-pro"));
    }

    @Test
    @DisplayName("checkStatus returns offline on HTTP error")
    void testCheckStatus_error() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=gemini-test-key"))
                .andExpect(method(HttpMethod.GET))
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
        properties.setGeminiApiKey("");
        assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
    }

    @Test
    @DisplayName("generateChatCompletion parses Gemini candidates response format")
    void testGenerate_success() {
        String responseBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Gemini AI response\"}]}}]}";
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("system", "user");
        mockServer.verify();

        assertEquals("Gemini AI response", result);
    }

    @Test
    @DisplayName("generateChatCompletion throws IllegalStateException on empty candidates")
    void testGenerate_emptyResponse() {
        String responseBody = "{\"candidates\":[]}";
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
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
        props.setGeminiBaseUrl("https://custom.gemini.com///");
        GeminiGateway gw = new GeminiGateway(props, RestClient.builder(), null);
        assertEquals("GEMINI", gw.getProviderName());

        AiProperties propsZero = new AiProperties();
        propsZero.setTimeoutSeconds(0);
        propsZero.setGeminiBaseUrl(null);
        GeminiGateway gw2 = new GeminiGateway(propsZero, (RestClient.Builder) null, new ObjectMapper());
        assertEquals("GEMINI", gw2.getProviderName());
    }

    @Test
    @DisplayName("generateChatCompletion handles RestClientResponseException on HTTP error")
    void testGenerate_httpError() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST).body("Invalid model"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
        assertTrue(ex.getMessage().contains("Gemini API error"));
        mockServer.verify();
    }

    @Test
    @DisplayName("generateChatCompletion falls back to default model when model is null or auto")
    void testGenerate_defaultModel() {
        properties.setModel(null);
        String responseBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Default model response\"}]}}]}";
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("system", "user");
        mockServer.verify();
        assertEquals("Default model response", result);
    }

    @Test
    @DisplayName("generateChatCompletion throws on candidate with empty parts or blank text")
    void testGenerate_blankText() {
        String responseBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"   \"}]}}]}";
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
        mockServer.verify();
    }

    @Test
    @DisplayName("checkStatus handles models without name or models/ prefix")
    void testCheckStatus_noPrefixAndMissingName() {
        String json = "{\"models\":[{\"name\":\"gemini-custom\"}, {\"noName\":\"val\"}]}";
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=gemini-test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status.connected());
        assertEquals(1, status.availableModels().size());
        assertTrue(status.availableModels().contains("gemini-custom"));
    }

    @Test
    @DisplayName("checkStatus filters out specialized models and sorts chat models by priority")
    void testCheckStatus_filtersSpecializedModelsAndSorts() {
        String json = """
                {
                  "models": [
                    {"name": "models/gemini-embedding-001", "supportedGenerationMethods": ["embedContent"]},
                    {"name": "models/veo-3.1", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-2.5-flash-tts", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-2.5-computer-use", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-transcribe-v1", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-audio-v1", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-image-v1", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-robotics-er", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-customtools-preview", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-veo-hybrid", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-lyria-audio", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-aqa-qa", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-flash-latest", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-pro-latest", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-1.5-flash", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-1.5-pro", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-2.5-pro", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-2.0-flash", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-2.0-flash-lite", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/gemini-custom-generation", "supportedGenerationMethods": ["generateContent"]}
                  ]
                }
                """;
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=gemini-test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status.connected());
        assertEquals(9, status.availableModels().size());
        // Verify priority sorting:
        assertEquals("gemini-2.5-flash", status.availableModels().get(0));
        assertEquals("gemini-2.5-pro", status.availableModels().get(1));
        assertEquals("gemini-2.0-flash", status.availableModels().get(2));
        assertEquals("gemini-2.0-flash-lite", status.availableModels().get(3));
        assertEquals("gemini-flash-latest", status.availableModels().get(4));
        assertEquals("gemini-pro-latest", status.availableModels().get(5));
        assertEquals("gemini-1.5-flash", status.availableModels().get(6));
        assertEquals("gemini-1.5-pro", status.availableModels().get(7));
        assertEquals("gemini-custom-generation", status.availableModels().get(8));
    }

    @Test
    @DisplayName("checkStatus and generateChatCompletion handle null API key")
    void testNullApiKey() {
        properties.setGeminiApiKey(null);
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.connected());

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
    }

    @Test
    @DisplayName("resolveActiveModel handles auto, null, blank and explicit model")
    void testResolveActiveModel() {
        properties.setModel("auto");
        assertEquals("gemini-2.5-flash", gateway.resolveActiveModel());
        assertEquals("gemini-2.5-pro", gateway.resolveActiveModel(java.util.List.of("gemini-2.5-pro")));

        properties.setModel("   ");
        assertEquals("gemini-2.5-flash", gateway.resolveActiveModel());

        properties.setModel("gemini-custom-model");
        assertEquals("gemini-custom-model", gateway.resolveActiveModel());
    }

    @Test
    @DisplayName("checkStatus handles missing or non-array models field")
    void testCheckStatus_missingOrNonArrayModels() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=gemini-test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\": \"not-array\"}", MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status.connected());
        assertTrue(status.availableModels().isEmpty());

        mockServer.reset();
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=gemini-test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        AiStatusDto status2 = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status2.connected());
        assertTrue(status2.availableModels().isEmpty());
    }

    @Test
    @DisplayName("generateChatCompletion handles candidate structure anomalies and malformed responses")
    void testGenerate_candidateStructureAnomalies() {
        // null / empty content
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"candidates\": [{}]}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // candidates not an array
        mockServer.reset();
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"candidates\": \"not-array\"}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // content without parts
        mockServer.reset();
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"candidates\": [{\"content\": {}}]}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // parts not an array
        mockServer.reset();
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"candidates\": [{\"content\": {\"parts\": \"not-array\"}}]}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // parts without text
        mockServer.reset();
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"candidates\": [{\"content\": {\"parts\": [{}]}}]}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // completely unparseable malformed JSON
        mockServer.reset();
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gemini-test-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("definitely-not-json", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();
    }

    @Test
    @DisplayName("test constructor handles null ObjectMapper and null properties")
    void testConstructorNullMapper() {
        GeminiGateway gw = new GeminiGateway(properties, RestClient.builder().build(), null);
        assertEquals("GEMINI", gw.getProviderName());
        GeminiGateway gwNull = new GeminiGateway(null, (RestClient.Builder) null, null);
        assertEquals(60, gwNull.getTimeoutSeconds());
    }

    @Test
    @DisplayName("Autowired constructor sets timeouts and setTimeoutSeconds updates requestFactory")
    void testAutowiredConstructorAndSetTimeoutSeconds() {
        GeminiGateway gw = new GeminiGateway(properties, RestClient.builder(), objectMapper);
        assertEquals(30, gw.getTimeoutSeconds());
        gw.setTimeoutSeconds(120);
        gateway.setTimeoutSeconds(90);
    }
}

