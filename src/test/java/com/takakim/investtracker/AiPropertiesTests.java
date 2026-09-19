package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;

import com.takakim.investtracker.config.AiProperties;
import org.junit.jupiter.api.Test;

class AiPropertiesTests {

    @Test
    void testGettersAndSetters() {
        AiProperties props = new AiProperties();

        assertTrue(props.isEnabled());
        assertEquals("http://localhost:1234", props.getBaseUrl());
        assertEquals("gemma4-12b", props.getModel());
        assertEquals("", props.getApiKey());
        assertEquals(0.2, props.getTemperature());
        assertEquals(4096, props.getMaxTokens());
        assertEquals("none", props.getReasoningEffort());
        assertEquals(60, props.getTimeoutSeconds());

        props.setEnabled(false);
        assertFalse(props.isEnabled());

        props.setBaseUrl("http://remote-server:1234");
        assertEquals("http://remote-server:1234", props.getBaseUrl());

        props.setModel("llama3-8b");
        assertEquals("llama3-8b", props.getModel());

        props.setApiKey("test-key");
        assertEquals("test-key", props.getApiKey());

        props.setTemperature(0.7);
        assertEquals(0.7, props.getTemperature());

        props.setMaxTokens(8192);
        assertEquals(8192, props.getMaxTokens());

        props.setReasoningEffort("low");
        assertEquals("low", props.getReasoningEffort());

        props.setTimeoutSeconds(120);
        assertEquals(120, props.getTimeoutSeconds());
    }
}
