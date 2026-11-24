package com.hjl.oj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.model.dto.ai.AiSettingSaveRequest;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.entity.UserAiSetting;
import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.vo.AiSettingVO;

/**
 * 用户 AI 设置：API Key（加密存储）、模型、提示模式
 */
public interface AiSettingService extends IService<UserAiSetting> {

    AiSettingVO getSettingVO(User loginUser);

    /**
     * 保存设置；带了新 Key 时先校验再加密存储
     */
    AiSettingVO saveSetting(User loginUser, AiSettingSaveRequest request);

    /**
     * 删除 Key（清空密文和后 4 位，其余设置保留）
     */
    AiSettingVO deleteKey(User loginUser);

    /**
     * 取本次调用要用的凭证；真实模式下没配 Key 时抛出 AI_KEY_NOT_CONFIGURED
     */
    AiCredential requireCredential(long userId);

    /**
     * 是否可以调用模型：模拟模式恒为 true，真实模式下要求已配置 Key
     */
    boolean canCallModel(long userId);

    HintModeEnum getHintMode(long userId);

    /**
     * 用户所选模型的上下文窗口（token 数）
     */
    int getContextWindow(long userId);
}
