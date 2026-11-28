package com.hjl.oj.companion.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.companion.note.NoteEditSession;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.note.NoteQueryRequest;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.NoteSummaryVO;
import com.hjl.oj.model.vo.NoteVO;
import com.hjl.oj.service.NoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteToolsTest {

    private final NoteService noteService = mock(NoteService.class);

    private final User me = new User();

    private NoteTools tools;

    @BeforeEach
    void setUp() {
        me.setId(1L);
        tools = tools(4000);
    }

    @Test
    void listingThisQuestionFiltersByIt() {
        when(noteService.listMyNotes(any(), eq(me))).thenReturn(page(summary(9L, 7L, "两数之和")));

        String text = tools.listNotesOnThisQuestion();

        ArgumentCaptor<NoteQueryRequest> query = ArgumentCaptor.forClass(NoteQueryRequest.class);
        verify(noteService).listMyNotes(query.capture(), eq(me));
        assertEquals(7L, query.getValue().getQuestionId());
        assertTrue(text.contains("笔记 id 9") && text.contains("「复盘」"), text);
    }

    @Test
    void searchSaysWhereEachNoteBelongs() {
        when(noteService.listMyNotes(any(), eq(me))).thenReturn(page(summary(9L, 8L, "三数之和"), summary(10L, null, null)));

        String text = tools.searchMyNotes(" 双指针 ");

        assertTrue(text.contains("挂在题目「三数之和」上"), text);
        assertTrue(text.contains("自由笔记"), text);
        when(noteService.listMyNotes(any(), eq(me))).thenReturn(page());
        assertTrue(tools.searchMyNotes("单调栈").contains("没有找到包含「单调栈」"));
    }

    @Test
    void readingIsLimitedToTheUsersOwnNotesAndTruncated() {
        NoteVO note = new NoteVO();
        note.setId(9L);
        note.setTitle("复盘");
        note.setQuestionId(7L);
        note.setQuestionTitle("两数之和");
        note.setContent("很长的正文".repeat(30));
        when(noteService.getNote(9L, me)).thenReturn(note);
        when(noteService.getNote(11L, me)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在"));

        NoteTools small = tools(60);
        assertTrue(small.readNote("9").contains("正文过长"));
        assertTrue(small.readNote("9").contains("挂在本题「两数之和」上"));
        assertTrue(tools.readNote("11").contains("只能看用户自己的笔记"));
        assertTrue(tools.readNote("x").contains("格式不对"));
    }

    private NoteTools tools(int maxResultChars) {
        return new NoteTools(me, 7L, noteService, new NoteEditSession(me, 7L, "两数之和", 0, "", noteService),
                maxResultChars);
    }

    private static Page<NoteSummaryVO> page(NoteSummaryVO... records) {
        Page<NoteSummaryVO> page = new Page<>(1, 10, records.length);
        page.setRecords(List.of(records));
        return page;
    }

    private static NoteSummaryVO summary(long id, Long questionId, String questionTitle) {
        NoteSummaryVO vo = new NoteSummaryVO();
        vo.setId(id);
        vo.setTitle("复盘");
        vo.setQuestionId(questionId);
        vo.setQuestionTitle(questionTitle);
        return vo;
    }
}
