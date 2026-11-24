package com.hjl.oj.model.dto.ai;

import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.model.dto.companion.CodeSnapshot;
import com.hjl.oj.model.dto.companion.CompanionChatRequest;
import com.hjl.oj.model.entity.UserAiSetting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LogInterceptor 会以 INFO 级别打印接口入参的 toString：API Key 不能进日志，用户代码和对话内容也不进日志。
 */
class SensitiveToStringTest {

    @Test
    void saveRequestHidesApiKey() {
        AiSettingSaveRequest request = new AiSettingSaveRequest();
        request.setApiKey("sk-very-secret");
        request.setModel("deepseek-flash");

        assertFalse(request.toString().contains("sk-very-secret"));
        assertTrue(request.toString().contains("deepseek-flash"));
    }

    @Test
    void settingEntityAndCredentialHideKey() {
        UserAiSetting setting = new UserAiSetting();
        setting.setEncryptedKey("v1:ciphertext");
        setting.setKeyHint("****cret");

        assertFalse(setting.toString().contains("v1:ciphertext"));
        assertFalse(new AiCredential("sk-very-secret", "deepseek-flash").toString().contains("sk-very-secret"));
    }

    @Test
    void chatRequestHidesMessageAndCode() {
        CodeSnapshot snapshot = new CodeSnapshot();
        snapshot.setCode("int secretAlgorithm = 42;");
        snapshot.setLanguage("java");
        CompanionChatRequest request = new CompanionChatRequest();
        request.setQuestionId(3L);
        request.setMessage("我的私人问题");
        request.setSnapshot(snapshot);

        assertFalse(request.toString().contains("我的私人问题"));
        assertFalse(request.toString().contains("secretAlgorithm"));
        assertTrue(request.toString().contains("java"));
    }
}
