package com.hjl.oj.companion.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.hjl.oj.model.enums.CompanionMoodEnum;
import com.hjl.oj.model.enums.HintTrackEnum;

import java.util.Optional;

/**
 * 校验提交后反应：心情在调色板内、强度 1-3、方向合法、话不过长
 */
public class ReactionAnswerGuardrail extends JsonAnswerGuardrail {

    static final int MAX_MESSAGE_LENGTH = 150;

    @Override
    protected Optional<String> check(JsonNode root) {
        Optional<String> problem = checkEnum(root, "mood", CompanionMoodEnum.class)
                .or(() -> checkEnum(root, "track", HintTrackEnum.class))
                .or(() -> checkText(root, "message", MAX_MESSAGE_LENGTH));
        if (problem.isPresent()) {
            return problem;
        }
        JsonNode intensity = root.get("intensity");
        if (intensity == null || !intensity.canConvertToInt() || intensity.asInt() < 1 || intensity.asInt() > 3) {
            return Optional.of("intensity 必须是 1 到 3 的整数");
        }
        return Optional.empty();
    }
}
