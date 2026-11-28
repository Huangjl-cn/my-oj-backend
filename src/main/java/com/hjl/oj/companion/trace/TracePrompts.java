package com.hjl.oj.companion.trace;

import com.hjl.oj.companion.memory.MemoryTranscript;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把发给模型的内容整理成轨迹里好看的样子：用户消息拆成段落、工具说明拆成参数表。
 */
public final class TracePrompts {

    /**
     * 提示词里每段的标题单独起一行：【标题】，后面可以跟同一行的内容（如「【场景】用户在陪练面板里和你聊天。」）
     */
    private static final Pattern SECTION_TITLE = Pattern.compile("^【([^】\\n]{1,20})】(.*)$");

    private TracePrompts() {
    }

    /**
     * 按【标题】拆段；第一个标题之前的内容是一段没有标题的
     */
    public static List<TracePromptSection> split(String prompt) {
        List<TracePromptSection> sections = new ArrayList<>();
        if (prompt == null || prompt.isBlank()) {
            return sections;
        }
        String title = null;
        StringBuilder text = new StringBuilder();
        for (String line : prompt.replace("\r\n", "\n").split("\n", -1)) {
            Matcher matcher = SECTION_TITLE.matcher(line);
            if (matcher.matches()) {
                add(sections, title, text);
                title = matcher.group(1);
                text.setLength(0);
                text.append(matcher.group(2).strip());
            } else {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(line);
            }
        }
        add(sections, title, text);
        return sections;
    }

    /**
     * 用户这一轮说的话（【用户说】那一段）；没有时返回 null
     */
    public static String userSays(List<TracePromptSection> sections) {
        return sections.stream()
                .filter(section -> MemoryTranscript.USER_SAYS.equals(section.getTitle()))
                .map(TracePromptSection::getText)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public static List<TraceTool> describe(List<ToolSpecification> specifications) {
        List<TraceTool> tools = new ArrayList<>();
        if (specifications == null) {
            return tools;
        }
        for (ToolSpecification specification : specifications) {
            List<TraceTool.Param> params = new ArrayList<>();
            JsonObjectSchema schema = specification.parameters();
            if (schema != null && schema.properties() != null) {
                List<String> required = schema.required() == null ? List.of() : schema.required();
                for (Map.Entry<String, JsonSchemaElement> property : schema.properties().entrySet()) {
                    params.add(new TraceTool.Param(property.getKey(), typeOf(property.getValue()),
                            property.getValue().description(), required.contains(property.getKey())));
                }
            }
            tools.add(new TraceTool(specification.name(), specification.description(), params));
        }
        return tools;
    }

    private static String typeOf(JsonSchemaElement element) {
        if (element instanceof JsonStringSchema) {
            return "string";
        }
        if (element instanceof JsonIntegerSchema) {
            return "integer";
        }
        if (element instanceof JsonNumberSchema) {
            return "number";
        }
        if (element instanceof JsonBooleanSchema) {
            return "boolean";
        }
        if (element instanceof JsonArraySchema) {
            return "array";
        }
        if (element instanceof JsonEnumSchema) {
            return "enum";
        }
        if (element instanceof JsonObjectSchema) {
            return "object";
        }
        return element == null ? "" : element.getClass().getSimpleName();
    }

    private static void add(List<TracePromptSection> sections, String title, StringBuilder text) {
        String body = text.toString().strip();
        if (title != null || !body.isEmpty()) {
            sections.add(new TracePromptSection(title, body));
        }
    }
}
