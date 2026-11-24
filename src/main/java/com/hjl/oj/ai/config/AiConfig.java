package com.hjl.oj.ai.config;

import com.hjl.oj.ai.call.AiCallGuard;
import com.hjl.oj.ai.key.ApiKeyCipher;
import com.hjl.oj.ai.key.ApiKeyVerifier;
import com.hjl.oj.ai.key.DeepSeekApiKeyVerifier;
import com.hjl.oj.ai.model.ChatModelProvider;
import com.hjl.oj.ai.model.DeepSeekChatModelProvider;
import com.hjl.oj.ai.model.mock.MockChatModelProvider;
import com.hjl.oj.ai.model.mock.MockReplyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 基础设施装配。配置在这里集中校验，缺项或非法时启动失败（fail-fast）。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private final AiProperties properties;

    public AiConfig(AiProperties properties) {
        this.properties = properties;
        validate(properties);
        log.info("AI 调用模式: {}", properties.getMode());
    }

    @Bean
    public ApiKeyCipher apiKeyCipher() {
        return new ApiKeyCipher(properties.getKeyEncryptionKey());
    }

    @Bean
    public ApiKeyVerifier apiKeyVerifier() {
        if (properties.getMode() == AiMode.MOCK) {
            return apiKey -> {
            };
        }
        AiProperties.DeepSeek deepseek = properties.getDeepseek();
        return new DeepSeekApiKeyVerifier(deepseek.getBaseUrl(), deepseek.getVerifyTimeout());
    }

    @Bean
    public ChatModelProvider chatModelProvider(ObjectProvider<MockReplyGenerator> mockReplyGenerator) {
        if (properties.getMode() == AiMode.MOCK) {
            return new MockChatModelProvider(mockReplyGenerator.getIfAvailable(
                    () -> (options, request) -> "（模拟模式）这是模拟模型的固定回复"));
        }
        return new DeepSeekChatModelProvider(properties.getDeepseek());
    }

    @Bean
    public AiCallGuard aiCallGuard() {
        return new AiCallGuard();
    }

    static void validate(AiProperties properties) {
        if (properties.getMode() == null) {
            throw new IllegalStateException("缺少 ai.mode（mock 或 live）");
        }
        AiProperties.DeepSeek deepseek = properties.getDeepseek();
        if (StringUtils.isBlank(deepseek.getBaseUrl())) {
            throw new IllegalStateException("缺少 ai.deepseek.base-url");
        }
        if (deepseek.getModels().isEmpty()) {
            throw new IllegalStateException("ai.deepseek.models 至少配置一个模型");
        }
        for (AiProperties.ModelOption model : deepseek.getModels()) {
            if (StringUtils.isBlank(model.getId()) || model.getContextWindow() <= 0) {
                throw new IllegalStateException("ai.deepseek.models 中每个模型都要配置 id 和 context-window");
            }
        }
        if (deepseek.findModel(deepseek.getDefaultModel()).isEmpty()) {
            throw new IllegalStateException("ai.deepseek.default-model 必须是 ai.deepseek.models 中的一个");
        }
        AiProperties.Companion companion = properties.getCompanion();
        if (companion.getCompactRatio() <= 0 || companion.getCompactRatio() >= 1) {
            throw new IllegalStateException("ai.companion.compact-ratio 必须在 0 和 1 之间");
        }
        if (companion.getKeepRecentRounds() < 1 || companion.getHistoryPageSize() < 1
                || companion.getRecentSubmissionCount() < 1) {
            throw new IllegalStateException("ai.companion 的轮数、条数配置必须大于 0");
        }
    }
}
