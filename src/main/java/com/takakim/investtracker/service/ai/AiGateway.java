package com.takakim.investtracker.service.ai;

import com.takakim.investtracker.service.ai.dto.AiStatusDto;

/**
 * Abstraction over LLM provider backends (LM Studio, OpenAI, Gemini, Anthropic).
 * Implementations are selected at startup via the AI_PROVIDER environment variable.
 */
public interface AiGateway {

    /**
     * Returns the connection / availability status of this provider.
     */
    AiStatusDto checkStatus();

    /**
     * Sends a system + user prompt pair and returns the raw text completion.
     *
     * @param systemPrompt the system context prompt
     * @param userPrompt   the user financial analysis prompt
     * @return the raw completion text from the model
     * @throws IllegalStateException if AI is disabled or communication fails
     */
    String generateChatCompletion(String systemPrompt, String userPrompt);

    /**
     * Returns the provider identifier (e.g. "LM_STUDIO", "OPENAI", "GEMINI", "ANTHROPIC").
     */
     String getProviderName();

    /**
     * Dynamically updates the HTTP read timeout (in seconds) for subsequent inference requests.
     */
    default void setTimeoutSeconds(int timeoutSeconds) {}

    /**
     * Returns the current timeout in seconds.
     */
    default int getTimeoutSeconds() { return 60; }
}

