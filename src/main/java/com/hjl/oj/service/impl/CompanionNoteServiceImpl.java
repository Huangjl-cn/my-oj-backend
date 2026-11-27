package com.hjl.oj.service.impl;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.companion.note.NoteChanges;
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
import com.hjl.oj.service.CompanionNoteService;
import com.hjl.oj.service.NoteService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 鸭鸭的笔记提议：预览和应用。
 *
 * <p>修改是按提议时的笔记算的（baseVersion）。笔记在这之后又被改过时，按修改明细在新内容上重新应用：
 * 原文找不到的跳过；整篇改写会覆盖那之后的改动，要用户确认。
 */
@Service
public class CompanionNoteServiceImpl implements CompanionNoteService {

    static final String STALE_MESSAGE = "笔记在这之后改过，这些修改都对不上了，重新让鸭鸭改一次吧";

    static final String OVERWRITE_MESSAGE = "笔记在这之后改过，应用会整篇覆盖那些改动，确认后再应用";

    private final CompanionMessageService companionMessageService;

    private final NoteService noteService;

    public CompanionNoteServiceImpl(CompanionMessageService companionMessageService, NoteService noteService) {
        this.companionMessageService = companionMessageService;
        this.noteService = noteService;
    }

    @Override
    public CompanionNotePreviewVO preview(User loginUser, long messageId, int index) {
        List<NoteProposal> proposals = requireProposals(loginUser, messageId);
        NoteProposal proposal = proposals.get(requireIndex(proposals, index));
        Resolution resolution = resolve(loginUser, proposal);
        CompanionNotePreviewVO vo = new CompanionNotePreviewVO();
        vo.setStatus(resolution.status());
        vo.setProposal(proposal);
        vo.setTitle(resolution.title());
        vo.setContent(resolution.content());
        vo.setCurrentContent(resolution.current() == null ? null : resolution.current().getContent());
        vo.setSkipped(resolution.skipped());
        vo.setNoteId(proposal.getNoteId());
        return vo;
    }

    @Override
    public CompanionNoteApplyVO apply(User loginUser, CompanionNoteApplyRequest request) {
        if (request == null || request.getMessageId() == null || request.getIndex() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<NoteProposal> proposals = new ArrayList<>(requireProposals(loginUser, request.getMessageId()));
        NoteProposal proposal = proposals.get(requireIndex(proposals, request.getIndex()));
        if (proposal.isApplied()) {
            // 重复点「应用」：不再写一遍，直接给那篇笔记
            return result(noteService.getNote(proposal.getNoteId(), loginUser), proposal, 0);
        }

        NoteVO note;
        int skipped = 0;
        if (NoteProposal.KIND_CREATE.equals(proposal.getKind())) {
            NoteAddRequest add = new NoteAddRequest();
            add.setTitle(proposal.getTitle());
            add.setContent(proposal.getContent());
            add.setQuestionId(proposal.getQuestionId());
            note = noteService.addNote(add, loginUser);
            proposal.setNoteId(note.getId());
        } else {
            Resolution resolution = resolve(loginUser, proposal);
            switch (resolution.status()) {
                case CompanionNotePreviewVO.MISSING ->
                        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "这篇笔记已经删除了");
                case CompanionNotePreviewVO.STALE -> throw new BusinessException(ErrorCode.CONFLICT_ERROR, STALE_MESSAGE);
                case CompanionNotePreviewVO.OVERWRITE -> {
                    if (!Boolean.TRUE.equals(request.getOverwrite())) {
                        throw new BusinessException(ErrorCode.CONFLICT_ERROR, OVERWRITE_MESSAGE);
                    }
                }
                default -> {
                }
            }
            NoteUpdateRequest update = new NoteUpdateRequest();
            update.setId(proposal.getNoteId());
            update.setTitle(resolution.title());
            update.setContent(resolution.content());
            // 用刚读到的版本号保存：读完到保存之间又被改了，照样冲突
            update.setVersion(resolution.current().getVersion());
            note = noteService.updateNote(update, loginUser);
            skipped = resolution.skipped();
        }
        proposal.setApplied(true);
        companionMessageService.updateNoteEdits(request.getMessageId(), proposals);
        return result(note, proposal, skipped);
    }

    /**
     * 提议按笔记现在的内容算出的结果
     *
     * @param current 笔记现在的样子（只有修改有）
     */
    record Resolution(String status, String title, String content, NoteVO current, int skipped) {
    }

    Resolution resolve(User loginUser, NoteProposal proposal) {
        if (proposal.isApplied()) {
            return new Resolution(CompanionNotePreviewVO.APPLIED, proposal.getTitle(), proposal.getContent(), null, 0);
        }
        if (NoteProposal.KIND_CREATE.equals(proposal.getKind())) {
            return new Resolution(CompanionNotePreviewVO.READY, proposal.getTitle(), proposal.getContent(), null, 0);
        }
        NoteVO current;
        try {
            current = noteService.getNote(proposal.getNoteId(), loginUser);
        } catch (BusinessException e) {
            if (e.getCode() != ErrorCode.NOT_FOUND_ERROR.getCode()) {
                throw e;
            }
            return new Resolution(CompanionNotePreviewVO.MISSING, proposal.getTitle(), proposal.getContent(), null, 0);
        }
        if (Objects.equals(current.getVersion(), proposal.getBaseVersion())) {
            return new Resolution(CompanionNotePreviewVO.READY, proposal.getTitle(), proposal.getContent(), current, 0);
        }
        // 提议改了标题才用提议的标题，否则保留用户之后改的
        String title = Objects.equals(proposal.getTitle(), proposal.getPreviousTitle())
                ? current.getTitle() : proposal.getTitle();
        NoteChanges.Replay replay = NoteChanges.replay(current.getContent(), proposal.getChanges());
        String status;
        if (replay.rewritten()) {
            status = CompanionNotePreviewVO.OVERWRITE;
        } else if (replay.applied() == 0) {
            status = CompanionNotePreviewVO.STALE;
        } else if (replay.failed() > 0) {
            status = CompanionNotePreviewVO.PARTIAL;
        } else {
            status = CompanionNotePreviewVO.REBASED;
        }
        return new Resolution(status, title, replay.content(), current, replay.failed());
    }

    /**
     * 自己的消息上的提议；别人的消息当作不存在
     */
    private List<NoteProposal> requireProposals(User loginUser, long messageId) {
        CompanionMessage message = companionMessageService.getById(messageId);
        if (message == null || !Objects.equals(message.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "消息不存在");
        }
        return companionMessageService.noteEdits(message);
    }

    private static int requireIndex(List<NoteProposal> proposals, int index) {
        if (index < 0 || index >= proposals.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "这条提议不存在");
        }
        return index;
    }

    private static CompanionNoteApplyVO result(NoteVO note, NoteProposal proposal, int skipped) {
        CompanionNoteApplyVO vo = new CompanionNoteApplyVO();
        vo.setNote(note);
        vo.setProposal(proposal);
        vo.setSkipped(skipped);
        return vo;
    }
}
