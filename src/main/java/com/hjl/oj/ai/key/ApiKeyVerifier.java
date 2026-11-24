package com.hjl.oj.ai.key;

/**
 * 保存前校验用户 API Key 是否可用。
 */
public interface ApiKeyVerifier {

    /**
     * 校验通过直接返回；Key 无效或无法校验时抛出带具体原因的 BusinessException
     */
    void verify(String apiKey);
}
