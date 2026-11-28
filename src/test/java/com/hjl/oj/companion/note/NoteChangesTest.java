package com.hjl.oj.companion.note;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteChangesTest {

    @Test
    void replaceNeedsAUniqueMatch() {
        String content = "## 思路\r\n双指针\n## 坑\n双指针要注意边界";

        NoteChanges.Outcome ok = NoteChanges.apply(content, replace("## 思路\n双指针", "## 思路\n排序后双指针"));
        assertTrue(ok.ok());
        assertEquals("## 思路\n排序后双指针\n## 坑\n双指针要注意边界", ok.content());

        assertTrue(NoteChanges.apply(content, replace("双指针", "x")).failure().contains("出现了 2 次"));
        assertTrue(NoteChanges.apply(content, replace("哈希表", "x")).failure().contains("没找到"));
        assertFalse(NoteChanges.apply(content, replace("", "x")).ok());
    }

    @Test
    void appendLeavesOneBlankLine() {
        assertEquals("开头\n\n## 新的\n", NoteChanges.apply("开头\n\n\n", append("\n## 新的\n\n")).content());
        assertEquals("## 新的\n", NoteChanges.apply("", append("## 新的")).content());
        assertFalse(NoteChanges.apply("开头", append("  ")).ok());
    }

    @Test
    void replaySkipsChangesThatNoLongerMatch() {
        List<NoteChange> changes = List.of(replace("旧思路", "新思路"), replace("已删掉的一段", "x"), append("补充"));

        NoteChanges.Replay replay = NoteChanges.replay("旧思路\n用户后来加的", changes);

        assertEquals("新思路\n用户后来加的\n\n补充\n", replay.content());
        assertEquals(2, replay.applied());
        assertEquals(1, replay.failed());
        assertFalse(replay.rewritten());
        assertTrue(NoteChanges.replay("随便", List.of(new NoteChange(NoteChange.KIND_REWRITE, null, "整篇新的", "")))
                .rewritten());
    }

    private static NoteChange replace(String oldText, String newText) {
        return new NoteChange(NoteChange.KIND_REPLACE, oldText, newText, "");
    }

    private static NoteChange append(String text) {
        return new NoteChange(NoteChange.KIND_APPEND, null, text, "");
    }
}
