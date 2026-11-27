package com.hjl.oj.model.vo;

import com.hjl.oj.companion.trace.TracePromptSection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 轨迹里的一轮：一次聊天、提示、看答案、提交后反应或整理记忆
 */
@Data
public class CompanionTraceTurnVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "chat 聊天 / hint 提示 / solution 看答案 / reaction 提交后反应 / compaction 整理记忆")
    private String type;

    @Schema(description = "running 进行中 / ok 完成 / error 出错 / fallback 用了兜底话术")
    private String status;

    @Schema(description = "这一轮用户做了什么（聊天时是用户说的话）")
    @ToString.Exclude
    private String userText;

    @Schema(description = "发给模型的用户消息里除了「用户说」以外的各段（场景、提示状态、代码……）")
    private List<TracePromptSection> context = new ArrayList<>();

    @Schema(description = "这一轮用的系统提示词，CompanionTraceVO.systemPrompts 的下标；没调用模型时为空")
    private Integer systemPromptIndex;

    @Schema(description = "这一轮提供给模型的工具，CompanionTraceVO.toolSets 的下标；没调用模型时为空")
    private Integer toolSetIndex;

    @Schema(description = "这一轮产生的陪练消息 id")
    private Long messageId;

    private String error;

    @Schema(description = "开始时间；从记忆还原的没有")
    private Date startTime;

    @Schema(description = "耗时（毫秒）；进行中或从记忆还原的没有")
    private Integer durationMs;

    private List<CompanionTraceStepVO> steps = new ArrayList<>();
}
