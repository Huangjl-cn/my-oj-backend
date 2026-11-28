package com.hjl.oj.model.dto.questionlist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新建题单
 */
@Data
public class QuestionListAddRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "标题，1～40 个字")
    private String title;

    @Schema(description = "简介，最多 200 个字，可不填")
    private String description;

    @Schema(description = "建成官方题单（所有人可见），只有管理员能用")
    private Boolean official;
}
