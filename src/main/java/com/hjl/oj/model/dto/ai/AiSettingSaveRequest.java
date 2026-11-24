package com.hjl.oj.model.dto.ai;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保存用户 AI 设置。字段为空表示不修改该项。
 */
@Data
public class AiSettingSaveRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * DeepSeek API Key，保存前会先校验
     */
    @ToString.Exclude
    private String apiKey;

    /**
     * 模型 id，必须是后端配置的模型之一
     */
    private String model;

    /**
     * 提示模式：guided 循序渐进 / direct 有问必答
     */
    private String hintMode;
}
