package com.hjl.oj.service.impl;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.companion.note.NoteChange;
import com.hjl.oj.companion.note.NoteProposal;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.companion.CompanionNoteApplyRequest;
import com.hjl.oj.model.dto.note.NoteAddRequest;
import com.hjl.oj.model.dto.note.NoteUpdateRequest;
import com.hjl.oj.model.entity.CompanionMessage;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.CompanionNoteApplyVO;
import com.hjl.oj.model.vo.CompanionNotePreviewVO;
import com.hjl.oj.model.vo.NoteVO;
import com.hjl.oj.service.CompanionMessageService;
import com.hjl.oj.service.NoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionNoteServiceImplTest {

    private static final long MESSAGE_ID = 50L;

    private final CompanionMessageService messageService = mock(CompanionMessageService.class);

    private final NoteService noteService = mock(NoteService.class);

    private final CompanionNoteServiceImpl service = new CompanionNoteServiceImpl(messageService, noteService);

    private final User me = new User();

    private final List<NoteProposal> proposals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        me.setId(1L);
        CompanionMessage message = new CompanionMessage();
        message.setId(MESSAGE_ID);
        message.setUserId(1L);
        when(messageService.getById(MESSAGE_ID)).thenReturn(message);
        when(messageService.noteEdits(message)).thenAnswer(invocation -> proposals);
        when(noteService.updateNote(any(), any())).thenAnswer(invocation -> {
            NoteUpdateRequest update = invocation.getArgument(0);
            return note(update.getId(), update.getContent(), update.getVersion() + 1);
        });
    }

    @Test
    void applyingANewNoteCreatesItOnceAndMarksItApplied() {
        proposals.add(create());
        when(noteService.addNote(any(), any())).thenReturn(note(30L, "正文", 0));

        CompanionNoteApplyVO vo = service.apply(me, request(0, false));

        ArgumentCaptor<NoteAddRequest> added = ArgumentCaptor.forClass(NoteAddRequest.class);
        verify(noteService).addNote(added.capture(), eq(me));
        assertEquals("两数之和 笔记", added.getValue().getTitle());
        assertEquals(7L, added.getValue().getQuestionId());
        assertTrue(vo.getProposal().isApplied());
        assertEquals(30L, vo.getProposal().getNoteId());
        verify(messageService).updateNoteEdits(eq(MESSAGE_ID), anyList());

        // 再点一次：不再新建，直接给那篇
        when(noteService.getNote(30L, me)).thenReturn(note(30L, "正文", 0));
        assertEquals(30L, service.apply(me, request(0, false)).getNote().getId());
        verify(noteService).addNote(any(), any());
        assertEquals(CompanionNotePreviewVO.APPLIED, service.preview(me, MESSAGE_ID, 0).getStatus());
    }

    @Test
    void anUnchangedNoteGetsTheProposedContent() {
        proposals.add(update(List.of(replace("暴力", "哈希表")), "思路\n哈希表\n"));
        when(noteService.getNote(9L, me)).thenReturn(note(9L, "思路\n暴力\n", 3));

        assertEquals(CompanionNotePreviewVO.READY, service.preview(me, MESSAGE_ID, 0).getStatus());
        CompanionNoteApplyVO vo = service.apply(me, request(0, false));

        assertEquals("思路\n哈希表\n", vo.getNote().getContent());
        ArgumentCaptor<NoteUpdateRequest> saved = ArgumentCaptor.forClass(NoteUpdateRequest.class);
        verify(noteService).updateNote(saved.capture(), eq(me));
        assertEquals(3, saved.getValue().getVersion());
        assertEquals("笔记", saved.getValue().getTitle());
    }

    @Test
    void aNoteChangedSinceTheProposalGetsTheChangesReplayed() {
        proposals.add(update(List.of(replace("暴力", "哈希表"), replace("已经删掉的", "x")), "思路\n哈希表\n"));
        when(noteService.getNote(9L, me)).thenReturn(note(9L, "思路\n暴力\n用户后来加的\n", 5));

        CompanionNotePreviewVO preview = service.preview(me, MESSAGE_ID, 0);
        assertEquals(CompanionNotePreviewVO.PARTIAL, preview.getStatus());
        assertEquals(1, preview.getSkipped());
        assertEquals("思路\n哈希表\n用户后来加的\n", preview.getContent());

        CompanionNoteApplyVO vo = service.apply(me, request(0, false));
        assertEquals(1, vo.getSkipped());
        assertEquals("思路\n哈希表\n用户后来加的\n", vo.getNote().getContent());
    }

    @Test
    void staleAndOverwritingProposalsNeedTheUser() {
        proposals.add(update(List.of(replace("已经删掉的", "x")), "x"));
        proposals.add(update(List.of(new NoteChange(NoteChange.KIND_REWRITE, null, "整篇新的", "")), "整篇新的"));
        when(noteService.getNote(9L, me)).thenReturn(note(9L, "用户重写过的", 5));

        assertEquals(CompanionNotePreviewVO.STALE, service.preview(me, MESSAGE_ID, 0).getStatus());
        assertEquals(ErrorCode.CONFLICT_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.apply(me, request(0, false))).getCode());

        assertEquals(CompanionNotePreviewVO.OVERWRITE, service.preview(me, MESSAGE_ID, 1).getStatus());
        assertEquals(ErrorCode.CONFLICT_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.apply(me, request(1, false))).getCode());
        verify(noteService, never()).updateNote(any(), any());
        assertEquals("整篇新的", service.apply(me, request(1, true)).getNote().getContent());
    }

    @Test
    void deletedNotesAndOtherPeoplesMessagesAreNotFound() {
        proposals.add(update(List.of(replace("a", "b")), "b"));
        when(noteService.getNote(9L, me)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在"));
        assertEquals(CompanionNotePreviewVO.MISSING, service.preview(me, MESSAGE_ID, 0).getStatus());
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.apply(me, request(0, false))).getCode());

        User someoneElse = new User();
        someoneElse.setId(2L);
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.preview(someoneElse, MESSAGE_ID, 0)).getCode());
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.preview(me, MESSAGE_ID, 3)).getCode());
        verify(messageService, never()).updateNoteEdits(anyLong(), anyList());
    }

    private static CompanionNoteApplyRequest request(int index, boolean overwrite) {
        CompanionNoteApplyRequest request = new CompanionNoteApplyRequest();
        request.setMessageId(MESSAGE_ID);
        request.setIndex(index);
        request.setOverwrite(overwrite);
        return request;
    }

    private static NoteProposal create() {
        NoteProposal proposal = new NoteProposal();
        proposal.setKind(NoteProposal.KIND_CREATE);
        proposal.setTitle("两数之和 笔记");
        proposal.setQuestionId(7L);
        proposal.setContent("正文");
        return proposal;
    }

    private static NoteProposal update(List<NoteChange> changes, String content) {
        NoteProposal proposal = new NoteProposal();
        proposal.setKind(NoteProposal.KIND_UPDATE);
        proposal.setNoteId(9L);
        proposal.setBaseVersion(3);
        proposal.setTitle("笔记");
        proposal.setPreviousTitle("笔记");
        proposal.setChanges(new ArrayList<>(changes));
        proposal.setContent(content);
        return proposal;
    }

    private static NoteChange replace(String oldText, String newText) {
        return new NoteChange(NoteChange.KIND_REPLACE, oldText, newText, "");
    }

    private static NoteVO note(long id, String content, int version) {
        NoteVO note = new NoteVO();
        note.setId(id);
        note.setTitle("笔记");
        note.setContent(content);
        note.setVersion(version);
        return note;
    }
}
