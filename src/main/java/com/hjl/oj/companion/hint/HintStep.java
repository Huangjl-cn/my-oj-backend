package com.hjl.oj.companion.hint;

import com.hjl.oj.model.enums.HintTrackEnum;

/**
 * 提示档位中的一档。用户只看到名字和说明，不区分方向；模型按自己判断的方向取对应的规则。
 *
 * @param level       档位 0-5
 * @param title       给用户看的名字，如"点关键"
 * @param description 给用户看的一句说明
 * @param ideaRule    给模型看的规则：用户没思路时，这一档最多能透露什么
 * @param bugRule     给模型看的规则：用户思路对但代码有 bug 时，这一档最多能透露什么
 */
public record HintStep(int level, String title, String description, String ideaRule, String bugRule) {

    public String rule(HintTrackEnum track) {
        return track == HintTrackEnum.BUG ? bugRule : ideaRule;
    }
}
