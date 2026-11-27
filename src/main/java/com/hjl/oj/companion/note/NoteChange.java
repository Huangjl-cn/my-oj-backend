package com.hjl.oj.companion.note;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 对一篇已有笔记的一处修改（鸭鸭提议的）。笔记在提议之后又被改过时，按这些修改在新内容上重新应用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteChange implements Serializable {

    public static final String KIND_REPLACE = "replace";

    public static final String KIND_APPEND = "append";

    public static final String KIND_REWRITE = "rewrite";

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * replace 替换一段原文 / append 追加到末尾 / rewrite 整篇改写
     */
    private String kind;

    /**
     * 被替换的原文（只有 replace 有）
     */
    @ToString.Exclude
    private String oldText;

    /**
     * 新内容：replace 的替换内容、append 追加的内容、rewrite 的全文
     */
    @ToString.Exclude
    private String newText;

    /**
     * 为什么这样改，展示给用户
     */
    private String reason;
}
