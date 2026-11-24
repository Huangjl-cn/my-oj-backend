package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hjl.oj.ai.config.AiMode;
import com.hjl.oj.ai.config.AiProperties;
import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.ai.key.ApiKeyCipher;
import com.hjl.oj.ai.key.ApiKeyVerifier;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.mapper.UserAiSettingMapper;
import com.hjl.oj.model.dto.ai.AiSettingSaveRequest;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.entity.UserAiSetting;
import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.vo.AiModelOptionVO;
import com.hjl.oj.model.vo.AiSettingVO;
import com.hjl.oj.service.AiSettingService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户 AI 设置
 */
@Service
public class AiSettingServiceImpl extends ServiceImpl<UserAiSettingMapper, UserAiSetting> implements AiSettingService {

    private static final String PROVIDER_DEEPSEEK = "deepseek";

    private static final int KEY_HINT_LENGTH = 4;

    private static final int MAX_KEY_LENGTH = 256;

    private final AiProperties aiProperties;

    private final ApiKeyCipher apiKeyCipher;

    private final ApiKeyVerifier apiKeyVerifier;

    public AiSettingServiceImpl(AiProperties aiProperties, ApiKeyCipher apiKeyCipher, ApiKeyVerifier apiKeyVerifier) {
        this.aiProperties = aiProperties;
        this.apiKeyCipher = apiKeyCipher;
        this.apiKeyVerifier = apiKeyVerifier;
    }

    @Override
    public AiSettingVO getSettingVO(User loginUser) {
        return toVO(findByUserId(loginUser.getId()));
    }

    @Override
    public AiSettingVO saveSetting(User loginUser, AiSettingSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long userId = loginUser.getId();
        UserAiSetting update = new UserAiSetting();
        String apiKey = StringUtils.trimToNull(request.getApiKey());
        if (apiKey != null) {
            if (apiKey.length() > MAX_KEY_LENGTH || apiKey.chars().anyMatch(Character::isWhitespace)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "API Key 格式不正确");
            }
            apiKeyVerifier.verify(apiKey);
            update.setEncryptedKey(apiKeyCipher.encrypt(apiKey, userId));
            update.setKeyHint(maskKey(apiKey));
        }
        if (StringUtils.isNotBlank(request.getModel())) {
            if (aiProperties.getDeepseek().findModel(request.getModel()).isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的模型：" + request.getModel());
            }
            update.setModel(request.getModel());
        }
        if (StringUtils.isNotBlank(request.getHintMode())) {
            HintModeEnum hintMode = HintModeEnum.getEnumByValue(request.getHintMode());
            if (hintMode == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的提示模式：" + request.getHintMode());
            }
            update.setHintMode(hintMode.getValue());
        }
        upsert(userId, update);
        return toVO(findByUserId(userId));
    }

    @Override
    public AiSettingVO deleteKey(User loginUser) {
        long userId = loginUser.getId();
        this.update(new UpdateWrapper<UserAiSetting>()
                .eq("userId", userId)
                .set("encryptedKey", null)
                .set("keyHint", null));
        return toVO(findByUserId(userId));
    }

    @Override
    public AiCredential requireCredential(long userId) {
        UserAiSetting setting = findByUserId(userId);
        String modelName = resolveModel(setting);
        if (aiProperties.getMode() == AiMode.MOCK) {
            return new AiCredential(null, modelName);
        }
        if (setting == null || setting.getEncryptedKey() == null) {
            throw new BusinessException(ErrorCode.AI_KEY_NOT_CONFIGURED);
        }
        try {
            return new AiCredential(apiKeyCipher.decrypt(setting.getEncryptedKey(), userId), modelName);
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.AI_KEY_NOT_CONFIGURED, "保存的 API Key 已无法使用，请重新填写");
        }
    }

    @Override
    public boolean canCallModel(long userId) {
        if (aiProperties.getMode() == AiMode.MOCK) {
            return true;
        }
        UserAiSetting setting = findByUserId(userId);
        return setting != null && setting.getEncryptedKey() != null;
    }

    @Override
    public HintModeEnum getHintMode(long userId) {
        UserAiSetting setting = findByUserId(userId);
        HintModeEnum hintMode = setting == null ? null : HintModeEnum.getEnumByValue(setting.getHintMode());
        return hintMode == null ? HintModeEnum.GUIDED : hintMode;
    }

    @Override
    public int getContextWindow(long userId) {
        String modelName = resolveModel(findByUserId(userId));
        return aiProperties.getDeepseek().findModel(modelName)
                .map(AiProperties.ModelOption::getContextWindow)
                .orElseThrow();
    }

    private UserAiSetting findByUserId(long userId) {
        return this.getOne(new QueryWrapper<UserAiSetting>().eq("userId", userId));
    }

    private void upsert(long userId, UserAiSetting update) {
        UserAiSetting existing = findByUserId(userId);
        if (existing == null) {
            update.setUserId(userId);
            update.setProvider(PROVIDER_DEEPSEEK);
            if (update.getHintMode() == null) {
                update.setHintMode(HintModeEnum.GUIDED.getValue());
            }
            try {
                this.save(update);
                return;
            } catch (DuplicateKeyException e) {
                // 并发首次保存：另一请求已插入，转为更新
                existing = findByUserId(userId);
            }
        }
        update.setId(existing.getId());
        update.setUserId(null);
        this.updateById(update);
    }

    /**
     * 模型为空或已不在配置列表中时回落到默认模型
     */
    private String resolveModel(UserAiSetting setting) {
        AiProperties.DeepSeek deepseek = aiProperties.getDeepseek();
        if (setting != null && setting.getModel() != null && deepseek.findModel(setting.getModel()).isPresent()) {
            return setting.getModel();
        }
        return deepseek.getDefaultModel();
    }

    private AiSettingVO toVO(UserAiSetting setting) {
        boolean mockMode = aiProperties.getMode() == AiMode.MOCK;
        AiSettingVO vo = new AiSettingVO();
        vo.setProvider(PROVIDER_DEEPSEEK);
        vo.setKeyHint(setting == null ? null : setting.getKeyHint());
        vo.setKeyConfigured(mockMode || (setting != null && setting.getEncryptedKey() != null));
        vo.setModel(resolveModel(setting));
        HintModeEnum hintMode = setting == null ? null : HintModeEnum.getEnumByValue(setting.getHintMode());
        vo.setHintMode((hintMode == null ? HintModeEnum.GUIDED : hintMode).getValue());
        List<AiModelOptionVO> models = aiProperties.getDeepseek().getModels().stream()
                .map(model -> new AiModelOptionVO(model.getId(), model.getDisplayName(), model.getContextWindow()))
                .toList();
        vo.setModels(models);
        vo.setMockMode(mockMode);
        return vo;
    }

    static String maskKey(String apiKey) {
        String tail = apiKey.length() <= KEY_HINT_LENGTH ? apiKey : apiKey.substring(apiKey.length() - KEY_HINT_LENGTH);
        return "****" + tail;
    }
}
