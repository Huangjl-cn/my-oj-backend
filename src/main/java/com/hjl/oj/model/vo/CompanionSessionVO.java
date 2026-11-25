package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 进入题目时拉取的陪练会话（含当前对话的最近消息）
 */
@Data
public class CompanionSessionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    @Schema(description = "当前对话 id，聊天、提示、看答案、整理记忆都带上它")
    private Long conversationId;

    @Schema(description = "提示档位 0-5，5 表示看过答案")
    private Integer hintLevel;

    @Schema(description = "实际生效的档位：有问必答模式下为 5")
    private Integer effectiveLevel;

    @Schema(description = "提示模式 guided / direct")
    private String hintMode;

    @Schema(description = "各档位的名字和说明，下标即档位（0-5）")
    private List<HintStepVO> hintSteps;

    @Schema(description = "能否调用模型：没配 Key 时为 false，前端引导用户去配置")
    private Boolean canCallModel;

    @Schema(description = "是否为模拟模式")
    private Boolean mockMode;

    @Schema(description = "陪练的名字")
    private String personaName;

    @Schema(description = "当前对话的上下文占用")
    private ContextUsageVO contextUsage;

    @Schema(description = "当前对话最近的消息，按时间正序")
    private List<CompanionMessageVO> messages;

    @Schema(description = "当前对话是否还有更早的消息")
    private Boolean hasMoreMessages;
}
