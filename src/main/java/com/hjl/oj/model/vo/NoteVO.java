package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 一篇笔记（含正文）
 */
@Data
public class NoteVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    @ToString.Exclude
    private String content;

    @Schema(description = "关联的题目 id，自由笔记为空")
    private Long questionId;

    @Schema(description = "关联题目的标题；题目已删除时为空")
    private String questionTitle;

    @Schema(description = "关联题目的标签（含难度标签）")
    private List<String> questionTags;

    @Schema(description = "版本号，保存时原样带回")
    private Integer version;

    private Date createTime;

    private Date updateTime;
}
