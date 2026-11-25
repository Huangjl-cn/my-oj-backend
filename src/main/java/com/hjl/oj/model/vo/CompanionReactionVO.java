package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交后陪练的反应
 */
@Data
public class CompanionReactionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long messageId;

    private Long submissionId;

    @Schema(description = "心情，见心情调色板")
    private String mood;

    @Schema(description = "心情强度 1-3")
    private Integer moodIntensity;

    @Schema(description = "反应记在哪段对话里")
    private Long conversationId;

    private String message;

    @Schema(description = "是否为兜底话术（没配 Key 或模型调用失败）")
    private Boolean fallback;
}
