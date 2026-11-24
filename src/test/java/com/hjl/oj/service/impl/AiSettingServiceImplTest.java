package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hjl.oj.ai.config.AiMode;
import com.hjl.oj.ai.config.AiProperties;
import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.ai.key.ApiKeyCipher;
import com.hjl.oj.ai.key.ApiKeyVerifier;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.ai.AiSettingSaveRequest;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.entity.UserAiSetting;
import com.hjl.oj.model.vo.AiSettingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class AiSettingServiceImplTest {

    private static final long USER_ID = 7L;

    private final ApiKeyCipher cipher = new ApiKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));

    private final ApiKeyVerifier verifier = mock(ApiKeyVerifier.class);

    private AiProperties properties;

    private AiSettingServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setMode(AiMode.LIVE);
        properties.getDeepseek().setDefaultModel("deepseek-flash");
        AiProperties.ModelOption flash = new AiProperties.ModelOption();
        flash.setId("deepseek-flash");
        flash.setContextWindow(1_000_000);
        AiProperties.ModelOption pro = new AiProperties.ModelOption();
        pro.setId("deepseek-v4-pro");
        pro.setContextWindow(1_000_000);
        properties.getDeepseek().setModels(List.of(flash, pro));
        service = spy(new AiSettingServiceImpl(properties, cipher, verifier));
        user = new User();
        user.setId(USER_ID);
    }

    @Test
    void verifiesThenStoresOnlyCiphertextAndHint() {
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(UserAiSetting.class));
        AiSettingSaveRequest request = new AiSettingSaveRequest();
        request.setApiKey("  sk-abcdef1234  ");

        service.saveSetting(user, request);

        verify(verifier).verify("sk-abcdef1234");
        ArgumentCaptor<UserAiSetting> saved = ArgumentCaptor.forClass(UserAiSetting.class);
        verify(service).save(saved.capture());
        assertFalse(saved.getValue().getEncryptedKey().contains("sk-abcdef1234"));
        assertEquals("sk-abcdef1234", cipher.decrypt(saved.getValue().getEncryptedKey(), USER_ID));
        assertEquals("****1234", saved.getValue().getKeyHint());
        assertEquals("guided", saved.getValue().getHintMode());
    }

    @Test
    void invalidKeyIsNotSaved() {
        doThrow(new BusinessException(ErrorCode.AI_KEY_INVALID)).when(verifier).verify(anyString());
        AiSettingSaveRequest request = new AiSettingSaveRequest();
        request.setApiKey("sk-wrong");

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveSetting(user, request));

        assertEquals(ErrorCode.AI_KEY_INVALID.getCode(), error.getCode());
        verify(service, never()).save(any(UserAiSetting.class));
    }

    @Test
    void rejectsUnknownModelAndHintMode() {
        AiSettingSaveRequest badModel = new AiSettingSaveRequest();
        badModel.setModel("gpt-x");
        AiSettingSaveRequest badMode = new AiSettingSaveRequest();
        badMode.setHintMode("random");

        assertThrows(BusinessException.class, () -> service.saveSetting(user, badModel));
        assertThrows(BusinessException.class, () -> service.saveSetting(user, badMode));
    }

    @Test
    void liveModeRequiresSavedKey() {
        doReturn(null).when(service).getOne(any(Wrapper.class));

        BusinessException error = assertThrows(BusinessException.class, () -> service.requireCredential(USER_ID));

        assertEquals(ErrorCode.AI_KEY_NOT_CONFIGURED.getCode(), error.getCode());
        assertFalse(service.canCallModel(USER_ID));
    }

    @Test
    void decryptsSavedKeyWithChosenModel() {
        UserAiSetting setting = new UserAiSetting();
        setting.setEncryptedKey(cipher.encrypt("sk-live", USER_ID));
        setting.setModel("deepseek-v4-pro");
        doReturn(setting).when(service).getOne(any(Wrapper.class));

        AiCredential credential = service.requireCredential(USER_ID);

        assertEquals("sk-live", credential.apiKey());
        assertEquals("deepseek-v4-pro", credential.modelName());
        assertFalse(credential.toString().contains("sk-live"));
    }

    @Test
    void mockModeNeedsNoKey() {
        properties.setMode(AiMode.MOCK);
        doReturn(null).when(service).getOne(any(Wrapper.class));

        AiCredential credential = service.requireCredential(USER_ID);
        AiSettingVO vo = service.getSettingVO(user);

        assertNull(credential.apiKey());
        assertEquals("deepseek-flash", credential.modelName());
        assertTrue(vo.getKeyConfigured());
        assertTrue(vo.getMockMode());
        assertEquals(2, vo.getModels().size());
    }

    @Test
    void masksAllButLastFourCharacters() {
        assertEquals("****wxyz", AiSettingServiceImpl.maskKey("sk-abcdefwxyz"));
        assertEquals("****abc", AiSettingServiceImpl.maskKey("abc"));
    }
}
