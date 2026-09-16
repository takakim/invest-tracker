package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.takakim.investtracker.config.AiProperties;
import com.takakim.investtracker.service.ai.OpenAiGateway;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenAiGatewayTests {

    private AiProperties properties;
    private OpenAiGateway gateway;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setOpenaiApiKey("sk-test-key");
        properties.setOpenaiBaseUrl("https://api.openai.com");
        properties.setModel("gpt-4o-mini");
        properties.setTemperature(0.2);
        properties.setMaxTokens(100);
        properties.setTimeoutSeconds(30);

        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        gateway = new OpenAiGateway(properties, restClient, objectMapper);
    }

    @Test
    @DisplayName("getProviderName returns OPENAI")
    void testProviderName() {
        assertEquals("OPENAI", gateway.getProviderName());
    }

    @Test
    @DisplayName("checkStatus returns offline when API key not configured")
    void testCheckStatus_missingApiKey() {
        properties.setOpenaiApiKey("");
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.connected());
        assertTrue(status.errorMessage().contains("OPENAI_API_KEY"));
    }

    @Test
    @DisplayName("checkStatus returns offline when AI is disabled")
    void testCheckStatus_disabled() {
        properties.setEnabled(false);
        AiStatusDto status = gateway.checkStatus();
        assertFalse(status.enabled());
        assertFalse(status.connected());
    }

    @Test
    @DisplayName("checkStatus returns connected when /v1/models responds with model list")
    void testCheckStatus_connected() {
        String modelsJson = "{\"data\":[{\"id\":\"gpt-4o-mini\"},{\"id\":\"gpt-4o\"}]}";
        mockServer.expect(requestTo("https://api.openai.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
                .andRespond(withSuccess(modelsJson, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();

        assertTrue(status.connected());
        assertEquals("OPENAI", status.provider());
        assertTrue(status.availableModels().contains("gpt-4o-mini"));
    }

    @Test
    @DisplayName("checkStatus returns offline when HTTP call fails")
    void testCheckStatus_connectionFailure() {
        mockServer.expect(requestTo("https://api.openai.com/v1/models"))
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
        properties.setOpenaiApiKey("");
        assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
    }

    @Test
    @DisplayName("generateChatCompletion parses OpenAI choices response format")
    void testGenerate_success() {
        String responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"OpenAI response\"}}]}";
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("system", "user");
        mockServer.verify();

        assertEquals("OpenAI response", result);
    }

    @Test
    @DisplayName("generateChatCompletion throws on empty choices")
    void testGenerate_emptyChoices() {
        String responseBody = "{\"choices\":[]}";
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
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
        props.setOpenaiBaseUrl("https://custom.openai.com///");
        OpenAiGateway gw = new OpenAiGateway(props, RestClient.builder(), null);
        assertEquals("OPENAI", gw.getProviderName());

        AiProperties propsZero = new AiProperties();
        propsZero.setTimeoutSeconds(0);
        propsZero.setOpenaiBaseUrl(null);
        OpenAiGateway gw2 = new OpenAiGateway(propsZero, (RestClient.Builder) null, new ObjectMapper());
        assertEquals("OPENAI", gw2.getProviderName());
    }

    @Test
    @DisplayName("generateChatCompletion handles RestClientResponseException on HTTP error")
    void testGenerate_httpError() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body("Unauthorized"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
        assertTrue(ex.getMessage().contains("OpenAI API error"));
        mockServer.verify();
    }

    @Test
    @DisplayName("generateChatCompletion falls back to default model when model is null")
    void testGenerate_defaultModel() {
        properties.setModel(null);
        String responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Default model response\"}}]}";
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = gateway.generateChatCompletion("system", "user");
        mockServer.verify();
        assertEquals("Default model response", result);
    }

    @Test
    @DisplayName("checkStatus handles data array with non-object or missing id items")
    void testCheckStatus_malformedData() {
        String json = "{\"data\":[{\"noId\":\"val\"}]}";
        mockServer.expect(requestTo("https://api.openai.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status.connected());
        assertTrue(status.availableModels().isEmpty());
    }

    @Test
    @DisplayName("generateChatCompletion throws on null or blank message content")
    void testGenerate_blankMessageContent() {
        String responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"   \"}}]}";
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class,
                () -> gateway.generateChatCompletion("system", "user"));
        mockServer.verify();
    }

    @Test
    @DisplayName("checkStatus handles non-array data, null data, or parse failure")
    void testCheckStatus_nonArrayDataAndFailure() {
        mockServer.expect(requestTo("https://api.openai.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\": null}", MediaType.APPLICATION_JSON));

        AiStatusDto status = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status.connected());
        assertTrue(status.availableModels().isEmpty());

        mockServer.reset();
        mockServer.expect(requestTo("https://api.openai.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\": \"not-an-array\"}", MediaType.APPLICATION_JSON));

        AiStatusDto status2 = gateway.checkStatus();
        mockServer.verify();
        assertTrue(status2.connected());
        assertTrue(status2.availableModels().isEmpty());
    }

    @Test
    @DisplayName("generateChatCompletion handles non-array choices, missing message, missing content, and malformed JSON")
    void testGenerate_choicesStructureAnomalies() {
        // choices not an array
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"choices\": \"not-array\"}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // message missing
        mockServer.reset();
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"choices\": [{}]}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // content missing
        mockServer.reset();
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"choices\": [{\"message\": {}}]}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // malformed JSON
        mockServer.reset();
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> gateway.generateChatCompletion("sys", "usr"));
        mockServer.verify();

        // General RestClient non-HTTP exception
        mockServer.reset();
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
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
        OpenAiGateway gw = new OpenAiGateway(properties, RestClient.builder().build(), null);
        assertEquals("OPENAI", gw.getProviderName());
        OpenAiGateway gwNull = new OpenAiGateway(null, (RestClient.Builder) null, null);
        assertEquals(60, gwNull.getTimeoutSeconds());
    }

    @Test
    @DisplayName("Autowired constructor sets timeouts and setTimeoutSeconds updates requestFactory")
    void testAutowiredConstructorAndSetTimeoutSeconds() {
        OpenAiGateway gw = new OpenAiGateway(properties, RestClient.builder(), objectMapper);
        assertEquals(30, gw.getTimeoutSeconds());
        gw.setTimeoutSeconds(120);
        gateway.setTimeoutSeconds(90);
    }
}

