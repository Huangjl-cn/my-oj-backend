package com.hjl.oj.config;

import com.hjl.oj.judge.codesandbox.auth.SandboxAuthSigner;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 代码沙箱调用鉴权配置。
 *
 * <p>密钥对用一次性工具生成（见 test 目录下的 SandboxAuthKeygenTool）：
 * 后端持私钥（本配置），沙箱持公钥。密钥缺失或非法时启动即失败（fail-fast），
 * 避免带着错误配置上线后所有判题请求才陆续失败。
 */
@Configuration
public class SandboxAuthConfig {

    @Bean
    public SandboxAuthSigner sandboxAuthSigner(
            @Value("${codesandbox.auth.key-id}") String keyId,
            @Value("${codesandbox.auth.private-key}") String privateKey) {
        if (StringUtils.isAnyBlank(keyId, privateKey)) {
            throw new IllegalStateException(
                    "沙箱鉴权配置缺失：codesandbox.auth.key-id 与 codesandbox.auth.private-key 必须配置，"
                            + "生产环境请通过 CODESANDBOX_AUTH_KEY_ID / CODESANDBOX_AUTH_PRIVATE_KEY 环境变量注入");
        }
        return SandboxAuthSigner.fromEncodedKey(keyId, privateKey);
    }
}
