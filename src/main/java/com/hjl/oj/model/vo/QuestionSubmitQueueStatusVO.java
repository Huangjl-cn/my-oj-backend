package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交排队状态封装类（判题削峰：排队人数展示）
 */
@Data
public class QuestionSubmitQueueStatusVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提交 id
     */
    @Schema(description = "提交 id")
    private Long submissionId;

    /**
     * 提交状态（0-待判题，1-判题中，2-成功，3-失败）
     */
    @Schema(description = "提交状态（0-待判题，1-判题中，2-成功，3-失败），非待判题状态前端可停止轮询")
    private Integer status;

    /**
     * 我前方排队的提交数
     */
    @Schema(description = "我前方排队的提交数（仅待判题时有效，其余状态为 0）")
    private Long aheadCount;

    /**
     * 待判题队列总长（含本人）
     */
    @Schema(description = "当前待判题队列总长（含本人）")
    private Long queueLength;
}
