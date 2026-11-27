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

    @Test
    void excerptKeepsMarkdownAndCutsAtWholeLines() {
        assertEquals("## 思路\n双指针", NoteSnippets.excerpt("## 思路\r\n双指针\n", 600));
        String long1 = "## 思路\n" + "一".repeat(30) + "\n" + "二".repeat(30);
        // 截在第二行中间：退回到整行，后面加省略
        assertEquals("## 思路\n" + "一".repeat(30) + "\n\n…", NoteSnippets.excerpt(long1, 50));
        assertEquals("", NoteSnippets.excerpt("  ", 10));
    }

    @Test
    void excerptClosesACodeBlockItCutsInto() {
        String markdown = "说明\n```java\nint a = 1;\nint b = 2;\nint c = 3;\n```";
        String excerpt = NoteSnippets.excerpt(markdown, 34);
        assertEquals("说明\n```java\nint a = 1;\nint b = 2;\n```\n\n…", excerpt);
    }
}
