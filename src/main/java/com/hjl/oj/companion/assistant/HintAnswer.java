package com.hjl.oj.companion.assistant;

import com.hjl.oj.model.enums.HintTrackEnum;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;

/**
 * 分级提示的结构化输出
 */
@Data
public class HintAnswer {

    @Description("你判断的方向：没思路选 IDEA，思路对了但代码有 bug 选 BUG")
    private HintTrackEnum track;

    @Description("给用户的提示：口语化，严格遵守这一级允许透露的内容，不超过 200 字")
    private String message;

    @Description("可选的代码修改建议，没有就给空数组；oldText 必须从用户代码中原样复制且唯一，不带行号")
    private List<HintEditAnswer> edits;
}
