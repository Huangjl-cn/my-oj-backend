package com.hjl.oj.companion.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.hjl.oj.model.enums.HintTrackEnum;

import java.util.Optional;

/**
 * 校验分级提示：方向合法、提示不过长、修改建议结构正确。修改是否越级由 CodeEditSession 另行把关。
 */
public class HintAnswerGuardrail extends JsonAnswerGuardrail {

    static final int MAX_MESSAGE_LENGTH = 400;

    static final int MAX_EDITS = 5;

    @Override
    protected Optional<String> check(JsonNode root) {
        Optional<String> problem = checkEnum(root, "track", HintTrackEnum.class)
                .or(() -> checkText(root, "message", MAX_MESSAGE_LENGTH));
        if (problem.isPresent()) {
            return problem;
        }
        JsonNode edits = root.get("edits");
        if (edits == null || edits.isNull()) {
            return Optional.empty();
        }
        if (!edits.isArray()) {
            return Optional.of("edits 必须是数组");
        }
        if (edits.size() > MAX_EDITS) {
            return Optional.of("edits 最多 " + MAX_EDITS + " 处");
        }
        for (JsonNode edit : edits) {
            if (!edit.isObject() || !edit.path("oldText").isTextual() || !edit.path("newText").isTextual()) {
                return Optional.of("edits 中每一项都要有字符串类型的 oldText 和 newText");
            }
        }
        return Optional.empty();
    }
}
