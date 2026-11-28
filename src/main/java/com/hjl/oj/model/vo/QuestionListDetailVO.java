package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题单详情：题单本身和里面的题（按题单里的顺序）
 */
@Data
public class QuestionListDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private QuestionListVO list;

    @Schema(description = "题单里的题，按题单里的顺序；已删除的题不在其中")
    private List<QuestionListItemVO> questions;
}
