package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一个标签下有几篇笔记（按笔记挂的题的标签算）
 */
@Data
@Schema(description = "一个标签下有几篇笔记")
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tag;

    private Integer count;
}
