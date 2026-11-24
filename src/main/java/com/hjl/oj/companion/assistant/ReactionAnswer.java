package com.hjl.oj.companion.assistant;

import com.hjl.oj.model.enums.CompanionMoodEnum;
import com.hjl.oj.model.enums.HintTrackEnum;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 提交后反应的结构化输出。字段说明会被框架写进"按 JSON 格式回答"的要求里。
 */
@Data
public class ReactionAnswer {

    @Description("从心情调色板里挑一个最贴合此刻的心情")
    private CompanionMoodEnum mood;

    @Description("心情强度：1 轻微，2 明显，3 强烈")
    private Integer intensity;

    @Description("你判断用户现在是没思路（IDEA），还是思路对了但代码有 bug（BUG）；通过时按这次的写法判断")
    private HintTrackEnum track;

    @Description("对用户说的话：口语化，1 到 3 句，不超过 80 个字")
    private String message;
}
