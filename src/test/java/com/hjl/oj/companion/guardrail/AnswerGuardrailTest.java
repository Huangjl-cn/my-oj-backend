package com.hjl.oj.companion.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerGuardrailTest {

    private final ReactionAnswerGuardrail reactionGuardrail = new ReactionAnswerGuardrail();

    private final HintAnswerGuardrail hintGuardrail = new HintAnswerGuardrail();

    @Test
    void acceptsValidReaction() {
        OutputGuardrailResult result = reactionGuardrail.validate(AiMessage.from(
                "{\"mood\":\"ENCOURAGING\",\"intensity\":2,\"track\":\"BUG\",\"message\":\"差一点了！\"}"));

        assertEquals(OutputGuardrailResult.Result.SUCCESS, result.result());
    }

    @Test
    void acceptsJsonWrappedInCodeFence() {
        OutputGuardrailResult result = reactionGuardrail.validate(AiMessage.from(
                "```json\n{\"mood\":\"PROUD\",\"intensity\":1,\"track\":\"IDEA\",\"message\":\"漂亮\"}\n```"));

        assertEquals(OutputGuardrailResult.Result.SUCCESS, result.result());
    }

    @Test
    void repromptsWhenMoodIsOutsidePalette() {
        OutputGuardrailResult result = reactionGuardrail.validate(AiMessage.from(
                "{\"mood\":\"ANGRY\",\"intensity\":2,\"track\":\"BUG\",\"message\":\"……\"}"));

        assertTrue(result.isReprompt());
        assertTrue(result.getReprompt().orElseThrow().contains("mood"));
    }

    @Test
    void repromptsOnInvalidJsonOrIntensity() {
        assertTrue(reactionGuardrail.validate(AiMessage.from("不是 JSON")).isReprompt());
        assertTrue(reactionGuardrail.validate(AiMessage.from(
                "{\"mood\":\"PROUD\",\"intensity\":7,\"track\":\"IDEA\",\"message\":\"好\"}")).isReprompt());
    }

    @Test
    void validatesHintEditsStructure() {
        assertEquals(OutputGuardrailResult.Result.SUCCESS, hintGuardrail.validate(AiMessage.from(
                "{\"track\":\"IDEA\",\"message\":\"想想数据规模\",\"edits\":[]}")).result());
        assertTrue(hintGuardrail.validate(AiMessage.from(
                "{\"track\":\"IDEA\",\"message\":\"看这里\",\"edits\":[{\"oldText\":1}]}")).isReprompt());
        assertTrue(hintGuardrail.validate(AiMessage.from(
                "{\"track\":\"IDEA\",\"message\":\"" + "长".repeat(HintAnswerGuardrail.MAX_MESSAGE_LENGTH + 1)
                        + "\",\"edits\":[]}")).isReprompt());
    }
}
