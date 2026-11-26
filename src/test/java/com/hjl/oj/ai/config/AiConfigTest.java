package com.hjl.oj.ai.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiConfigTest {

    @Test
    void acceptsCompleteConfiguration() {
        assertDoesNotThrow(() -> AiConfig.validate(validProperties()));
    }

    @Test
    void missingModeFailsFast() {
        AiProperties properties = validProperties();
        properties.setMode(null);

        assertThrows(IllegalStateException.class, () -> AiConfig.validate(properties));
    }

    @Test
    void defaultModelMustBeConfigured() {
        AiProperties properties = validProperties();
        properties.getDeepseek().setDefaultModel("unknown-model");

        assertThrows(IllegalStateException.class, () -> AiConfig.validate(properties));
    }

    @Test
    void compactRatioMustBeBetweenZeroAndOne() {
        AiProperties properties = validProperties();
        properties.getCompanion().setCompactRatio(1.5);

        assertThrows(IllegalStateException.class, () -> AiConfig.validate(properties));
    }

    static AiProperties validProperties() {
        AiProperties properties = new AiProperties();
        properties.setMode(AiMode.MOCK);
        properties.getDeepseek().setBaseUrl("https://api.deepseek.com");
        properties.getDeepseek().setDefaultModel("deepseek-flash");
        AiProperties.ModelOption model = new AiProperties.ModelOption();
        model.setId("deepseek-flash");
        model.setDisplayName("DeepSeek Flash");
        model.setContextWindow(1_000_000);
        properties.getDeepseek().setModels(List.of(model));
        properties.getCompanion().setPersonaName("鸭鸭");
        return properties;
    }
}
