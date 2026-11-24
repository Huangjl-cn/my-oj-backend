package com.hjl.oj.ai.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 相关配置（前缀 ai），启动时由 AiConfig 校验，缺项或取值非法直接启动失败。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * 调用模式，按环境在 profile 文件中配置
     */
    private AiMode mode;

    /**
     * 用户 API Key 的加密主密钥：Base64 编码的 32 字节
     */
    @ToString.Exclude
    private String keyEncryptionKey;

    private DeepSeek deepseek = new DeepSeek();

    private Companion companion = new Companion();

    @Data
    public static class DeepSeek {

        private String baseUrl;

        private String defaultModel;

        private Duration timeout = Duration.ofSeconds(180);

        private Duration verifyTimeout = Duration.ofSeconds(10);

        private List<ModelOption> models = new ArrayList<>();

        public Optional<ModelOption> findModel(String modelId) {
            return models.stream().filter(model -> model.getId().equals(modelId)).findFirst();
        }
    }

    @Data
    public static class ModelOption {

        private String id;

        private String displayName;

        /**
         * 上下文窗口（token 数）
         */
        private int contextWindow;
    }

    @Data
    public static class Companion {

        /**
         * 陪练角色的名字
         */
        private String personaName;

        /**
         * 上下文占用达到窗口的该比例时，发送前自动压缩记忆
         */
        private double compactRatio = 0.8;

        /**
         * 压缩时原样保留的最近轮数
         */
        private int keepRecentRounds = 4;

        private int maxCodeChars = 20000;

        private int maxToolResultChars = 4000;

        private int recentSubmissionCount = 5;

        private int historyPageSize = 30;
    }
}
