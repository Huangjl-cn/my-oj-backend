package com.hjl.oj.companion.tool;

import com.hjl.oj.companion.edit.CodeEditSession;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 给模型改用户代码的工具：修改只记录在本次请求的修改会话里，用户预览后决定是否应用。
 */
public class CodeEditTools {

    private final CodeEditSession editSession;

    public CodeEditTools(CodeEditSession editSession) {
        this.editSession = editSession;
    }

    @Tool("修改用户编辑器里的代码：把一段原文替换成新内容。原文必须从用户代码里原样复制（不带行号前缀），并且在代码中只出现一次；"
            + "修改不会直接生效，用户会先预览再决定是否应用；修改范围受当前提示等级限制，越界会被拒绝")
    public String editCode(@P("要替换的原文，原样复制且在代码中唯一") String oldText,
                           @P("替换后的新内容") String newText,
                           @P("为什么这样改，一句话，会展示给用户") String reason) {
        return editSession.replace(oldText, newText, reason);
    }
}
