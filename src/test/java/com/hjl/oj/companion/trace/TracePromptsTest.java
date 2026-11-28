package com.hjl.oj.companion.trace;

import com.hjl.oj.companion.tool.NoteTools;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracePromptsTest {

    @Test
    void userMessagesSplitIntoTitledSections() {
        List<TracePromptSection> sections = TracePrompts.split("""
                【场景】用户在陪练面板里和你聊天。
                - 先回应用户这句话本身

                【当前提示状态】
                - 档位：第 2 级「点关键」

                【用户当前代码】（java，行号只用于定位，不属于代码）
                ```
                1| int a = 1;
                ```

                【用户说】
                这里为什么超时？""");

        assertEquals(List.of("场景", "当前提示状态", "用户当前代码", "用户说"),
                sections.stream().map(TracePromptSection::getTitle).toList());
        assertEquals("用户在陪练面板里和你聊天。\n- 先回应用户这句话本身", sections.get(0).getText());
        assertTrue(sections.get(2).getText().startsWith("（java，行号只用于定位"));
        assertEquals("这里为什么超时？", TracePrompts.userSays(sections));
    }

    @Test
    void textBeforeTheFirstTitleIsKept() {
        List<TracePromptSection> sections = TracePrompts.split("开头\n【用户说】\n你好");
        assertNull(sections.get(0).getTitle());
        assertEquals("开头", sections.get(0).getText());
        assertTrue(TracePrompts.split("  ").isEmpty());
        assertNull(TracePrompts.userSays(TracePrompts.split("没有用户说")));
    }

    @Test
    void toolsAreDescribedWithTheirParameters() {
        List<TraceTool> tools = TracePrompts.describe(ToolSpecifications.toolSpecificationsFrom(NoteTools.class));

        TraceTool create = tools.stream().filter(tool -> tool.getName().equals("createNote")).findFirst().orElseThrow();
        assertTrue(create.getDescription().contains("新建"));
        TraceTool.Param link = create.getParameters().stream()
                .filter(param -> param.getName().equals("linkToThisQuestion")).findFirst().orElseThrow();
        assertEquals("boolean", link.getType());
        assertTrue(link.isRequired());
        assertTrue(TracePrompts.describe(null).isEmpty());
    }
}
