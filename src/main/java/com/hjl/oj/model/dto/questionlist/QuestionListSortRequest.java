package com.hjl.oj.model.dto.questionlist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题单里的题重新排序
 */
@Data
public class QuestionListSortRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long listId;

    @Schema(description = "题单里全部题目的 id，按新的顺序；必须和题单里现有的题一一对应")
    private List<Long> questionIds;
}
