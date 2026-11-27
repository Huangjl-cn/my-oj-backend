package com.hjl.oj.model.dto.questionlist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 收藏或取消收藏一道题
 */
@Data
public class QuestionFavoriteRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    @Schema(description = "true 收藏，false 取消收藏")
    private Boolean favorite;
}
