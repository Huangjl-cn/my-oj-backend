package com.hjl.oj.model.vo;

import com.hjl.oj.companion.note.NoteProposal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用笔记提议的结果
 */
@Data
public class CompanionNoteApplyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "写好的笔记")
    private NoteVO note;

    @Schema(description = "标记为已应用的提议")
    private NoteProposal proposal;

    @Schema(description = "笔记在提议之后改过、对不上被跳过的修改数")
    private Integer skipped;
}
