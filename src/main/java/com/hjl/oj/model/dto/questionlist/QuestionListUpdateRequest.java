package com.hjl.oj.model.dto.questionlist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改题单的标题和简介（收藏不能改）
 */
@Data
public class QuestionListUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "标题，1～40 个字")
    private String title;

    @Schema(description = "简介，最多 200 个字，可不填")
    private String description;
}
