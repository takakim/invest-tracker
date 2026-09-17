package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.takakim.investtracker.config.AiProperties;
import com.takakim.investtracker.service.ai.AiGatewayFactory;
import com.takakim.investtracker.service.ai.AnthropicGateway;
import com.takakim.investtracker.service.ai.GeminiGateway;
import com.takakim.investtracker.service.ai.LmStudioGateway;
import com.takakim.investtracker.service.ai.OpenAiGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiGatewayFactoryTests {

    @Mock private AiProperties properties;
    @Mock private LmStudioGateway lmStudioGateway;
    @Mock private OpenAiGateway openAiGateway;
    @Mock private GeminiGateway geminiGateway;
    @Mock private AnthropicGateway anthropicGateway;

    private AiGatewayFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AiGatewayFactory(properties, lmStudioGateway, openAiGateway, geminiGateway, anthropicGateway);
    }

    @Test
    @DisplayName("Returns LmStudioGateway when provider is LM_STUDIO")
    void testSelectsLmStudio() {
        when(properties.getProvider()).thenReturn("LM_STUDIO");
        assertSame(lmStudioGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("Returns LmStudioGateway when provider is null (default)")
    void testDefaultsToLmStudio() {
        when(properties.getProvider()).thenReturn(null);
        assertSame(lmStudioGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("Returns LmStudioGateway when provider is blank")
    void testBlankDefaultsToLmStudio() {
        when(properties.getProvider()).thenReturn("  ");
        assertSame(lmStudioGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("Returns OpenAiGateway when provider is OPENAI")
    void testSelectsOpenAi() {
        when(properties.getProvider()).thenReturn("OPENAI");
        assertSame(openAiGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("Returns OpenAiGateway when provider is lowercase openai")
    void testSelectsOpenAiCaseInsensitive() {
        when(properties.getProvider()).thenReturn("openai");
        assertSame(openAiGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("Returns GeminiGateway when provider is GEMINI")
    void testSelectsGemini() {
        when(properties.getProvider()).thenReturn("GEMINI");
        assertSame(geminiGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("Returns AnthropicGateway when provider is ANTHROPIC")
    void testSelectsAnthropic() {
        when(properties.getProvider()).thenReturn("ANTHROPIC");
        assertSame(anthropicGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("Returns LmStudioGateway for unknown provider")
    void testUnknownProviderDefaultsToLmStudio() {
        when(properties.getProvider()).thenReturn("UNKNOWN_PROVIDER");
        assertSame(lmStudioGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("AUTO provider selects LmStudioGateway when local is connected")
    void testAutoSelectsLmStudioWhenConnected() {
        when(properties.getProvider()).thenReturn("AUTO");
        com.takakim.investtracker.service.ai.dto.AiStatusDto connectedStatus =
                new com.takakim.investtracker.service.ai.dto.AiStatusDto(true, true, "LM_STUDIO", "http://localhost:1234", "auto", java.util.List.of(), null, 60);
        when(lmStudioGateway.checkStatus()).thenReturn(connectedStatus);

        assertSame(lmStudioGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("AUTO provider falls back to OpenAI when local is offline and OpenAI key is configured")
    void testAutoFallsBackToOpenAi() {
        when(properties.getProvider()).thenReturn("AUTO");
        com.takakim.investtracker.service.ai.dto.AiStatusDto offlineStatus =
                new com.takakim.investtracker.service.ai.dto.AiStatusDto(true, false, "LM_STUDIO", "http://localhost:1234", "auto", java.util.List.of(), "offline", 60);
        when(lmStudioGateway.checkStatus()).thenReturn(offlineStatus);
        when(properties.getOpenaiApiKey()).thenReturn("sk-test");

        assertSame(openAiGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("AUTO provider falls back to Gemini when local is offline, OpenAI unset, Gemini key configured")
    void testAutoFallsBackToGemini() {
        when(properties.getProvider()).thenReturn("AUTO");
        com.takakim.investtracker.service.ai.dto.AiStatusDto offlineStatus =
                new com.takakim.investtracker.service.ai.dto.AiStatusDto(true, false, "LM_STUDIO", "http://localhost:1234", "auto", java.util.List.of(), "offline", 60);
        when(lmStudioGateway.checkStatus()).thenReturn(offlineStatus);
        when(properties.getOpenaiApiKey()).thenReturn("");
        when(properties.getGeminiApiKey()).thenReturn("gemini-test");

        assertSame(geminiGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("AUTO provider falls back to Anthropic when local is offline, OpenAI & Gemini unset, Anthropic key configured")
    void testAutoFallsBackToAnthropic() {
        when(properties.getProvider()).thenReturn("AUTO");
        com.takakim.investtracker.service.ai.dto.AiStatusDto offlineStatus =
                new com.takakim.investtracker.service.ai.dto.AiStatusDto(true, false, "LM_STUDIO", "http://localhost:1234", "auto", java.util.List.of(), "offline", 60);
        when(lmStudioGateway.checkStatus()).thenReturn(offlineStatus);
        when(properties.getOpenaiApiKey()).thenReturn(null);
        when(properties.getGeminiApiKey()).thenReturn("");
        when(properties.getAnthropicApiKey()).thenReturn("sk-ant-test");

        assertSame(anthropicGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("AUTO provider handles LmStudioGateway exception gracefully")
    void testAutoHandlesLmStudioException() {
        when(properties.getProvider()).thenReturn("AUTO");
        when(lmStudioGateway.checkStatus()).thenThrow(new RuntimeException("Connection refused"));
        when(properties.getOpenaiApiKey()).thenReturn("sk-test");

        assertSame(openAiGateway, factory.getActiveGateway());
    }

    @Test
    @DisplayName("updateProvider updates properties when provider is valid and configured")
    void testUpdateProvider() {
        when(properties.getOpenaiApiKey()).thenReturn("sk-openai");
        when(properties.getGeminiApiKey()).thenReturn("gemini-key");

        factory.updateProvider("OPENAI");
        verify(properties).setProvider("OPENAI");

        factory.updateProvider("  gemini  ");
        verify(properties).setProvider("GEMINI");

        factory.updateProvider("LM_STUDIO");
        verify(properties).setProvider("LM_STUDIO");

        factory.updateProvider("AUTO");
        verify(properties).setProvider("AUTO");

        factory.updateProvider(null);
        factory.updateProvider("   ");
    }

    @Test
    @DisplayName("updateProvider throws IllegalArgumentException when third-party API key is not configured")
    void testUpdateProviderThrowsWhenApiKeyMissing() {
        when(properties.getOpenaiApiKey()).thenReturn("");
        assertThrows(IllegalArgumentException.class, () -> factory.updateProvider("OPENAI"));

        when(properties.getAnthropicApiKey()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> factory.updateProvider("ANTHROPIC"));
    }

    @Test
    @DisplayName("updateModel updates properties")
    void testUpdateModel() {
        factory.updateModel("google/gemma-4-e4b");
        verify(properties).setModel("google/gemma-4-e4b");

        factory.updateModel(null);
        factory.updateModel("   ");
    }

    @Test
    @DisplayName("getAvailableProviders filters providers based on configured API keys")
    void testGetAvailableProviders() {
        // Initially no third-party keys configured
        when(properties.getOpenaiApiKey()).thenReturn(null);
        when(properties.getGeminiApiKey()).thenReturn("");
        when(properties.getAnthropicApiKey()).thenReturn(null);

        java.util.List<String> providersOnlyLocal = factory.getAvailableProviders();
        assertEquals(java.util.List.of("LM_STUDIO"), providersOnlyLocal);

        // When Gemini key configured
        when(properties.getGeminiApiKey()).thenReturn("test-gemini-key");
        java.util.List<String> providersWithGemini = factory.getAvailableProviders();
        assertEquals(java.util.List.of("LM_STUDIO", "GEMINI"), providersWithGemini);

        // When all third-party keys configured
        when(properties.getOpenaiApiKey()).thenReturn("sk-test");
        when(properties.getAnthropicApiKey()).thenReturn("ant-test");
        java.util.List<String> providersAll = factory.getAvailableProviders();
        assertTrue(providersAll.containsAll(java.util.List.of("LM_STUDIO", "OPENAI", "GEMINI", "ANTHROPIC")));
    }

    @Test
    @DisplayName("updateTimeout updates properties and all gateways")
    void testUpdateTimeout() {
        factory.updateTimeout(45);
        verify(properties).setTimeoutSeconds(45);
        verify(lmStudioGateway).setTimeoutSeconds(45);
        verify(openAiGateway).setTimeoutSeconds(45);
        verify(geminiGateway).setTimeoutSeconds(45);
        verify(anthropicGateway).setTimeoutSeconds(45);
    }

    @Test
    @DisplayName("AUTO provider defaults to LM Studio when local is offline and no third party keys configured")
    void testAutoFallsBackToLmStudioDefault() {
        when(properties.getProvider()).thenReturn("AUTO");
        com.takakim.investtracker.service.ai.dto.AiStatusDto offlineStatus =
                new com.takakim.investtracker.service.ai.dto.AiStatusDto(true, false, "LM_STUDIO", "http://localhost:1234", "auto", java.util.List.of(), "offline", 60);
        when(lmStudioGateway.checkStatus()).thenReturn(offlineStatus);
        when(properties.getOpenaiApiKey()).thenReturn(null);
        when(properties.getGeminiApiKey()).thenReturn(null);
        when(properties.getAnthropicApiKey()).thenReturn(null);

        assertSame(lmStudioGateway, factory.getActiveGateway());
    }
}

