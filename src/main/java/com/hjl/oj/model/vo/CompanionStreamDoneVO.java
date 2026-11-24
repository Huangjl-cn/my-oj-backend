package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流式回答结束时的 done 事件
 */
@Data
public class CompanionStreamDoneVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "陪练这条回答的消息 id")
    private Long messageId;

    private Integer hintLevel;

    private String hintTrack;

    private ContextUsageVO contextUsage;

    @Schema(description = "本轮回答前是否自动整理过记忆")
    private Boolean compacted;
}
