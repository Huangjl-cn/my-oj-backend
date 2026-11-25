package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 对话列表里的一段对话
 */
@Data
public class CompanionConversationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "第一次提问的开头，还没提问时为空")
    private String title;

    @Schema(description = "消息条数")
    private Integer messageCount;

    @Schema(description = "是否为当前对话")
    private Boolean active;

    private Date createTime;

    @Schema(description = "最近活跃时间")
    private Date updateTime;
}
