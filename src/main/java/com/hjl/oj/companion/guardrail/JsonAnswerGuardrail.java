package com.hjl.oj.companion.guardrail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.util.Arrays;
import java.util.Optional;

/**
 * 校验模型的结构化回答：JSON 合法、字段取值在允许范围内。不合格时带上具体原因让模型重答（reprompt），
 * 重答仍不合格时框架抛出 OutputGuardrailException，由调用方降级处理。
 */
public abstract class JsonAnswerGuardrail implements OutputGuardrail {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM.text();
        if (text == null || text.isBlank()) {
            return reprompt("回答为空", "你的上一次回答是空的，请严格按要求的 JSON 格式回答。");
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(stripCodeFence(text));
        } catch (JsonProcessingException e) {
            return reprompt("回答不是合法 JSON", "你的上一次回答不是合法的 JSON，请只输出一个符合要求格式的 JSON 对象。");
        }
        if (root == null || !root.isObject()) {
            return reprompt("回答不是 JSON 对象", "请只输出一个 JSON 对象，不要输出数组或其他内容。");
        }
        Optional<String> problem = check(root);
        return problem
                .map(reason -> reprompt(reason, "你的上一次回答不符合要求：" + reason + "。请修正后按同样的 JSON 格式重新回答。"))
                .orElseGet(this::success);
    }

    /**
     * @return 不符合要求时返回原因
     */
    protected abstract Optional<String> check(JsonNode root);

    protected static Optional<String> checkText(JsonNode root, String field, int maxLength) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return Optional.of(field + " 必须是非空字符串");
        }
        if (node.asText().length() > maxLength) {
            return Optional.of(field + " 太长了，最多 " + maxLength + " 个字");
        }
        return Optional.empty();
    }

    protected static <E extends Enum<E>> Optional<String> checkEnum(JsonNode root, String field, Class<E> enumType) {
        JsonNode node = root.get(field);
        boolean valid = node != null && node.isTextual()
                && Arrays.stream(enumType.getEnumConstants()).anyMatch(constant -> constant.name().equals(node.asText()));
        if (!valid) {
            return Optional.of(field + " 必须是 " + Arrays.toString(enumType.getEnumConstants()) + " 之一");
        }
        return Optional.empty();
    }

    static String stripCodeFence(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int closingFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && closingFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, closingFence).strip();
            }
        }
        return trimmed;
    }
}
