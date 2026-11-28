package com.hjl.oj.model.vo;

import com.hjl.oj.companion.note.NoteProposal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预览一条笔记提议：按笔记现在的内容算出应用后的样子
 */
@Data
public class CompanionNotePreviewVO implements Serializable {

    public static final String READY = "ready";

    public static final String REBASED = "rebased";

    public static final String PARTIAL = "partial";

    public static final String OVERWRITE = "overwrite";

    public static final String STALE = "stale";

    public static final String MISSING = "missing";

    public static final String APPLIED = "applied";

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ready 可以直接应用 / rebased 笔记在这之后改过，修改都重新对上了 / partial 有几处对不上，会跳过 / "
            + "overwrite 笔记在这之后改过，应用会整篇覆盖（要确认） / stale 修改都对不上了，不能应用 / "
            + "missing 笔记已经删了 / applied 已经应用过")
    private String status;

    private NoteProposal proposal;

    @Schema(description = "应用后的标题")
    private String title;

    @Schema(description = "应用后的正文")
    @ToString.Exclude
    private String content;

    @Schema(description = "笔记现在的正文（只有修改有）")
    @ToString.Exclude
    private String currentContent;

    @Schema(description = "对不上、会被跳过的修改数")
    private Integer skipped;

    @Schema(description = "笔记 id：修改的那篇，或已经应用的新建笔记")
    private Long noteId;
}
