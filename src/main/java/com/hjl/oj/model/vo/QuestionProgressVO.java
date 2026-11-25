package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 当前用户的做题进度
 */
@Data
public class QuestionProgressVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "各难度的题目总数")
    private DifficultyCountVO total;

    @Schema(description = "各难度已通过的题目数")
    private DifficultyCountVO solved;

    @Schema(description = "做过的题目的状态，key 为题目 id；passed 已通过 / failed 没通过 / judging 判题中，没做过的不在其中")
    private Map<String, String> statuses;
}
