package com.hjl.oj.companion.note;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 鸭鸭提议的一篇笔记：新建一篇，或修改用户已有的一篇。只是提议，用户预览后点「应用」才写进笔记。
 */
@Data
@Schema(description = "鸭鸭提议的一篇笔记：新建一篇，或修改已有的一篇")
public class NoteProposal implements Serializable {

    public static final String KIND_CREATE = "create";

    public static final String KIND_UPDATE = "update";

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "create 新建 / update 修改已有的笔记")
    private String kind;

    @Schema(description = "修改的是哪篇笔记；新建的在应用后填上新笔记的 id")
    private Long noteId;

    @Schema(description = "修改时笔记的版本号；应用时笔记已经不是这个版本，说明在这之后改过")
    private Integer baseVersion;

    @Schema(description = "应用后的标题")
    private String title;

    @Schema(description = "修改前的标题（只有修改有）")
    private String previousTitle;

    @Schema(description = "挂在哪道题上；自由笔记为空")
    private Long questionId;

    @Schema(description = "所挂题目的标题")
    private String questionTitle;

    @Schema(description = "修改的明细（只有修改有），按顺序应用")
    private List<NoteChange> changes = new ArrayList<>();

    @Schema(description = "提议时算好的全文：新建的正文，或依次应用全部修改后的正文")
    @ToString.Exclude
    private String content;

    @Schema(description = "一句话说明，展示给用户")
    private String reason;

    @Schema(description = "用户已经点过「应用」")
    private boolean applied;
}
