package com.hjl.oj.companion.assistant;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 提示里附带的一处代码修改建议
 */
@Data
public class HintEditAnswer {

    @Description("要替换的原文")
    private String oldText;

    @Description("替换后的内容")
    private String newText;

    @Description("为什么这样改，一句话")
    private String reason;
}
