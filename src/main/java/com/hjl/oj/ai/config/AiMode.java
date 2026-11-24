package com.hjl.oj.ai.config;

/**
 * AI 调用模式。
 */
public enum AiMode {

    /**
     * 模拟模型：不需要任何 Key，不访问外部服务，用于本地开发和测试
     */
    MOCK,

    /**
     * 真实模型：使用用户自己保存的 API Key 调用 DeepSeek
     */
    LIVE
}
