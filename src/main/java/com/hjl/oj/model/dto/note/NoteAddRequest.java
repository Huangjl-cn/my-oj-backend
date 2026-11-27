package com.hjl.oj.model.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新建笔记
 */
@Data
public class NoteAddRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "标题，可不填：挂在题上时默认「题目名 笔记」，否则「未命名笔记」")
    private String title;

    @Schema(description = "正文（Markdown），可不填")
    @ToString.Exclude
    private String content;

    @Schema(description = "关联的题目 id，自由笔记不填")
    private Long questionId;
}
