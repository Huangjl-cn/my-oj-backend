package com.hjl.oj.companion.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 轨迹里存成 JSON 的几样东西（工具清单、模型发起的工具调用）的读写
 */
public final class TraceJson {

    public static final TypeReference<java.util.List<TraceTool>> TOOLS = new TypeReference<>() {
    };

    public static final TypeReference<java.util.List<TraceToolCall>> TOOL_CALLS = new TypeReference<>() {
    };

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private TraceJson() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("轨迹内容序列化失败", e);
        }
    }

    /**
     * 解析不了返回 null（只影响展示）
     */
    public static <T> T read(String json, TypeReference<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
