package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户 AI 设置（不含完整 Key）
 */
@Data
public class AiSettingVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "AI 厂商", example = "deepseek")
    private String provider;

    @Schema(description = "是否已配置 API Key；模拟模式下恒为 true")
    private Boolean keyConfigured;

    @Schema(description = "API Key 后 4 位，未配置时为空", example = "****a1b2")
    private String keyHint;

    @Schema(description = "当前使用的模型 id")
    private String model;

    @Schema(description = "可选的模型")
    private List<AiModelOptionVO> models;

    @Schema(description = "提示模式：guided 循序渐进 / direct 有问必答")
    private String hintMode;

    @Schema(description = "是否为模拟模式（开发环境默认开启，回复是固定话术）")
    private Boolean mockMode;
}
