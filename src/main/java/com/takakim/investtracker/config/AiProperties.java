package com.takakim.investtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled = true;

    /** Active provider: LM_STUDIO | OPENAI | GEMINI | ANTHROPIC */
    private String provider = "LM_STUDIO";

    /** Shared model name — interpretation depends on provider. */
    private String model = "gemma4-12b";

    /** LM Studio settings */
    private String baseUrl = "http://localhost:1234";
    private String apiKey = "";

    /** OpenAI settings */
    private String openaiApiKey = "";
    private String openaiBaseUrl = "https://api.openai.com";

    /** Google Gemini settings */
    private String geminiApiKey = "";
    private String geminiBaseUrl = "https://generativelanguage.googleapis.com";

    /** Anthropic Claude settings */
    private String anthropicApiKey = "";
    private String anthropicBaseUrl = "https://api.anthropic.com";

    /** Shared inference settings */
    private double temperature = 0.2;
    private int maxTokens = 2048;
    private int timeoutSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getOpenaiApiKey() { return openaiApiKey; }
    public void setOpenaiApiKey(String openaiApiKey) { this.openaiApiKey = openaiApiKey; }

    public String getOpenaiBaseUrl() { return openaiBaseUrl; }
    public void setOpenaiBaseUrl(String openaiBaseUrl) { this.openaiBaseUrl = openaiBaseUrl; }

    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }

    public String getGeminiBaseUrl() { return geminiBaseUrl; }
    public void setGeminiBaseUrl(String geminiBaseUrl) { this.geminiBaseUrl = geminiBaseUrl; }

    public String getAnthropicApiKey() { return anthropicApiKey; }
    public void setAnthropicApiKey(String anthropicApiKey) { this.anthropicApiKey = anthropicApiKey; }

    public String getAnthropicBaseUrl() { return anthropicBaseUrl; }
    public void setAnthropicBaseUrl(String anthropicBaseUrl) { this.anthropicBaseUrl = anthropicBaseUrl; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
