package com.hjl.oj.model.dto.note;

import com.hjl.oj.common.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 我的笔记列表：按最近修改排，可以按关键词和题目筛
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class NoteQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关键词，在标题和正文里找")
    private String keyword;

    @Schema(description = "只看挂在这道题上的笔记")
    private Long questionId;
}
