package com.hjl.oj.model.vo;

import com.hjl.oj.companion.trace.TraceTool;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一段对话的轨迹：鸭鸭每一轮收到了什么、调用了哪些工具、说了什么、花了多久
 */
@Data
public class CompanionTraceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long conversationId;

    @Schema(description = "对话标题；还没提问时为空")
    private String title;

    @Schema(description = "false：这段对话开始于记录轨迹之前，更早的部分没有记录")
    private boolean recordedFromStart;

    @Schema(description = "true：这段对话没有记录，下面是从鸭鸭的记忆还原的（没有耗时，整理过的部分只剩整理笔记）")
    private boolean fromMemory;

    @Schema(description = "用到的系统提示词（去重），各轮按下标引用")
    private List<String> systemPrompts = new ArrayList<>();

    @Schema(description = "用到的工具清单（去重），各轮按下标引用")
    private List<List<TraceTool>> toolSets = new ArrayList<>();

    private List<CompanionTraceTurnVO> turns = new ArrayList<>();
}
