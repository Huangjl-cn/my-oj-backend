package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 笔记列表里的一条（不含完整正文）
 */
@Data
public class NoteSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    @Schema(description = "正文开头的纯文字摘要（去掉了 Markdown 符号）")
    private String snippet;

    @Schema(description = "正文开头一段（Markdown，最多约 600 字），卡片墙上渲染着看")
    @ToString.Exclude
    private String excerpt;

    private Long questionId;

    private String questionTitle;

    private List<String> questionTags;

    private Date updateTime;
}
