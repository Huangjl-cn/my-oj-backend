package com.hjl.oj.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteSnippetsTest {

    @Test
    void markdownMarksAreRemoved() {
        String markdown = """
                # 思路

                > 先**排序**，再用 `双指针`。
                - [x] 处理[边界](https://example.com)
                1. 复杂度 O(n log n)

                ```java
                int a = 1;
                ```
                ---
                ![图](a.png)结束
                """;
        assertEquals("思路 先排序，再用 双指针。 处理边界 复杂度 O(n log n) [代码] 图结束", NoteSnippets.plain(markdown, 200));
    }

    @Test
    void longTextIsCutByCharactersNotBytes() {
        assertEquals("一二三…", NoteSnippets.plain("一二三四五", 3));
        assertEquals("😀😀…", NoteSnippets.plain("😀😀😀", 2));
        assertEquals("一二三", NoteSnippets.plain("一二三", 3));
        assertEquals("", NoteSnippets.plain("   ", 10));
        assertEquals("", NoteSnippets.plain(null, 10));
    }

    @Test
    void unclosedCodeFenceDoesNotLeakCode() {
        assertEquals("开头 [代码]", NoteSnippets.plain("开头\n```\nsecret code", 50));
    }
}
