package com.hjl.oj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.dto.note.NoteAddRequest;
import com.hjl.oj.model.dto.note.NoteQueryRequest;
import com.hjl.oj.model.dto.note.NoteUpdateRequest;
import com.hjl.oj.model.entity.Note;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.NoteSummaryVO;
import com.hjl.oj.model.vo.NoteVO;

/**
 * 笔记：只有作者自己能看能改，别人的笔记当作不存在
 */
public interface NoteService extends IService<Note> {

    int MAX_TITLE_LENGTH = 80;

    int MAX_CONTENT_LENGTH = 100_000;

    NoteVO addNote(NoteAddRequest request, User loginUser);

    /**
     * 保存标题和正文；版本号对不上（在别处改过了）抛 CONFLICT_ERROR
     */
    NoteVO updateNote(NoteUpdateRequest request, User loginUser);

    boolean deleteNote(long noteId, User loginUser);

    NoteVO getNote(long noteId, User loginUser);

    /**
     * 我的笔记，按最近修改排；keyword 在标题和正文里找
     */
    Page<NoteSummaryVO> listMyNotes(NoteQueryRequest request, User loginUser);
}
