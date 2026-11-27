package com.hjl.oj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjl.oj.common.BaseResponse;
import com.hjl.oj.common.DeleteRequest;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.common.ResultUtils;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.model.dto.note.NoteAddRequest;
import com.hjl.oj.model.dto.note.NoteQueryRequest;
import com.hjl.oj.model.dto.note.NoteUpdateRequest;
import com.hjl.oj.model.vo.NoteSummaryVO;
import com.hjl.oj.model.vo.NoteVO;
import com.hjl.oj.service.NoteService;
import com.hjl.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记（都要登录，只能看改自己的）
 */
@RestController
@RequestMapping("/note")
public class NoteController {

    @Resource
    private NoteService noteService;

    @Resource
    private UserService userService;

    /**
     * 新建笔记；questionId 不填就是自由笔记
     */
    @PostMapping("/add")
    public BaseResponse<NoteVO> addNote(@RequestBody NoteAddRequest addRequest, HttpServletRequest request) {
        return ResultUtils.success(noteService.addNote(addRequest, userService.getLoginUser(request)));
    }

    /**
     * 保存标题和正文；版本号对不上返回 40900（在别处改过了）
     */
    @PostMapping("/update")
    public BaseResponse<NoteVO> updateNote(@RequestBody NoteUpdateRequest updateRequest, HttpServletRequest request) {
        return ResultUtils.success(noteService.updateNote(updateRequest, userService.getLoginUser(request)));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteNote(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(noteService.deleteNote(deleteRequest.getId(), userService.getLoginUser(request)));
    }

    @GetMapping("/get")
    public BaseResponse<NoteVO> getNote(@RequestParam long id, HttpServletRequest request) {
        return ResultUtils.success(noteService.getNote(id, userService.getLoginUser(request)));
    }

    /**
     * 我的笔记，按最近修改排；keyword 在标题和正文里找，questionId 只看这道题的
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<NoteSummaryVO>> listMyNotes(@RequestBody NoteQueryRequest queryRequest,
                                                         HttpServletRequest request) {
        return ResultUtils.success(noteService.listMyNotes(queryRequest, userService.getLoginUser(request)));
    }
}
