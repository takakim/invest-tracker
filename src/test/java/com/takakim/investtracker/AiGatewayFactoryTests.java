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
    @DisplayName("updateTimeout updates properties and all gateways")
    void testUpdateTimeout() {
        factory.updateTimeout(120);

        verify(properties).setTimeoutSeconds(120);
        verify(lmStudioGateway).setTimeoutSeconds(120);
        verify(openAiGateway).setTimeoutSeconds(120);
        verify(geminiGateway).setTimeoutSeconds(120);
        verify(anthropicGateway).setTimeoutSeconds(120);
    }
}

