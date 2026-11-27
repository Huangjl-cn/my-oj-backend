package com.hjl.oj.companion.note;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.NoteVO;
import com.hjl.oj.service.NoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteEditSessionTest {

    private final NoteService noteService = mock(NoteService.class);

    private final User me = new User();

    private NoteEditSession session;

    @BeforeEach
    void setUp() {
        me.setId(1L);
        session = new NoteEditSession(me, 7L, "两数之和", 2, "int a = 1;", noteService);
        when(noteService.getNote(eq(9L), any())).thenReturn(note(9L, "思路\n暴力\n"));
        when(noteService.getNote(eq(10L), any())).thenThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在"));
    }

    @Test
    void newNotesCanBeLinkedToThisQuestionOrFree() {
        assertTrue(session.create("", "## 思路\n先排序", true, "记下思路").startsWith("已记录"));
        assertTrue(session.create("随手记", "复习计划", false, "").startsWith("已记录"));

        List<NoteProposal> proposals = session.proposals();
        assertEquals("两数之和 笔记", proposals.get(0).getTitle());
        assertEquals(7L, proposals.get(0).getQuestionId());
        assertEquals("## 思路\n先排序\n", proposals.get(0).getContent());
        assertNull(proposals.get(1).getQuestionId());
        assertTrue(session.create("空的", "  ", false, "").startsWith("提议失败"));
    }

    @Test
    void changesToOneNoteBecomeOneProposal() {
        assertTrue(session.replace("9", "暴力", "哈希表", "换成更快的做法").startsWith("已记录"));
        assertTrue(session.append("9", "## 坑\n下标别重复用", "补上踩过的坑").startsWith("已记录"));

        List<NoteProposal> proposals = session.proposals();
        assertEquals(1, proposals.size());
        NoteProposal proposal = proposals.get(0);
        assertEquals(NoteProposal.KIND_UPDATE, proposal.getKind());
        assertEquals(3, proposal.getBaseVersion());
        assertEquals("思路\n哈希表\n\n## 坑\n下标别重复用\n", proposal.getContent());
        assertEquals(2, proposal.getChanges().size());
        assertEquals("换成更快的做法；补上踩过的坑", proposal.getReason());
        // 同一篇只读一次
        verify(noteService, times(1)).getNote(eq(9L), any());
    }

    @Test
    void failedChangesAreNotRecorded() {
        assertTrue(session.replace("9", "不存在的原文", "x", "").startsWith("修改失败"));
        assertTrue(session.replace("10", "a", "b", "").contains("没有找到这篇笔记"));
        assertTrue(session.replace("abc", "a", "b", "").contains("格式不对"));
        assertTrue(session.proposals().isEmpty());
    }

    @Test
    void solutionCodeIsRejectedButTheUsersCodeIsFine() {
        String solution = "```\nfor (int i = 0; i < n; i++)\nfor (int j = i + 1; j < n; j++)\nif (a[i] + a[j] == t)\nreturn;\n```";
        assertTrue(session.create("", solution, true, "").startsWith("提议被拒绝"));
        assertTrue(session.append("9", solution, "").startsWith("修改被拒绝"));
        assertTrue(session.create("", "我的代码：\n```\nint a = 1;\n```", true, "").startsWith("已记录"));
    }

    @Test
    void rewriteCanRenameTheNote() {
        assertTrue(session.rewrite("9", "两数之和复盘", "## 思路\n哈希表\n", "重新整理").startsWith("已记录"));

        NoteProposal proposal = session.proposals().get(0);
        assertEquals("两数之和复盘", proposal.getTitle());
        assertEquals("笔记", proposal.getPreviousTitle());
        assertEquals("## 思路\n哈希表\n", proposal.getContent());
    }

    @Test
    void aTurnTouchesAtMostThreeNotes() {
        for (int i = 0; i < NoteEditSession.MAX_NOTES_PER_TURN; i++) {
            session.create("第 " + i + " 篇", "内容", false, "");
        }
        assertTrue(session.create("再来一篇", "内容", false, "").contains("最多涉及"));
    }

    private static NoteVO note(long id, String content) {
        NoteVO note = new NoteVO();
        note.setId(id);
        note.setTitle("笔记");
        note.setContent(content);
        note.setVersion(3);
        note.setQuestionId(7L);
        note.setQuestionTitle("两数之和");
        return note;
    }
}
