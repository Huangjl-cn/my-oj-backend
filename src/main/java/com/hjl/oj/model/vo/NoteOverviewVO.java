package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 我的笔记总览：卡片墙顶上的筛选用
 */
@Data
public class NoteOverviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer total;

    @Schema(description = "挂在题上的")
    private Integer linked;

    @Schema(description = "自由笔记")
    private Integer free;

    @Schema(description = "笔记最多的几个标签（按笔记挂的题的标签算，难度标签不算），多的在前")
    private List<NoteTagVO> tags;
}
