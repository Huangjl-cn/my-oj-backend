package com.hjl.oj.model.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保存笔记（标题和正文一起存）
 */
@Data
public class NoteUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    @ToString.Exclude
    private String content;

    @Schema(description = "读到的版本号；和库里的对不上说明在别处改过了，返回 40900")
    private Integer version;
}
