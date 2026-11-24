package com.hjl.oj.companion.tool;

import com.hjl.oj.companion.edit.CodeEditSession;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 整体替换用户代码的工具，只在不限制改代码的等级（第 5 级 / 有问必答）时提供给模型。
 */
public class CodeRewriteTools {

    private final CodeEditSession editSession;

    public CodeRewriteTools(CodeEditSession editSession) {
        this.editSession = editSession;
    }

    @Tool("用新代码整体替换用户编辑器里的代码，用户会先预览再决定是否应用")
    public String rewriteCode(@P("完整的新代码") String newCode, @P("一句话说明改了什么") String reason) {
        return editSession.rewrite(newCode, reason);
    }
}
