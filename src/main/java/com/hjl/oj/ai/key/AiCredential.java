package com.hjl.oj.ai.key;

/**
 * 一次调用要用的凭证：解密后的用户 API Key 和模型名。只在内存中短暂存在，不落盘、不进日志。
 *
 * @param apiKey    解密后的 Key；模拟模式下为 null
 * @param modelName 模型名
 */
public record AiCredential(String apiKey, String modelName) {

    @Override
    public String toString() {
        return "AiCredential[apiKey=***, modelName=" + modelName + "]";
    }
}
