package com.hjl.oj.controller;

import com.hjl.oj.common.BaseResponse;
import com.hjl.oj.common.DeleteRequest;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.common.ResultUtils;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.model.dto.questionlist.QuestionFavoriteRequest;
import com.hjl.oj.model.dto.questionlist.QuestionListAddRequest;
import com.hjl.oj.model.dto.questionlist.QuestionListItemRequest;
import com.hjl.oj.model.dto.questionlist.QuestionListSortRequest;
import com.hjl.oj.model.dto.questionlist.QuestionListUpdateRequest;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.QuestionListDetailVO;
import com.hjl.oj.model.vo.QuestionListVO;
import com.hjl.oj.service.QuestionListService;
import com.hjl.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题单：我的收藏、自建题单、官方题单（都要登录）
 */
@RestController
@RequestMapping("/question_list")
public class QuestionListController {

    @Resource
    private QuestionListService questionListService;

    @Resource
    private UserService userService;

    /**
     * 我的题单：「我的收藏」在最前，其余按最近修改排
     */
    @GetMapping("/mine")
    public BaseResponse<List<QuestionListVO>> listMyQuestionLists(HttpServletRequest request) {
        return ResultUtils.success(questionListService.listMine(userService.getLoginUser(request)));
    }

    /**
     * 官方题单
     */
    @GetMapping("/official")
    public BaseResponse<List<QuestionListVO>> listOfficialQuestionLists(HttpServletRequest request) {
        return ResultUtils.success(questionListService.listOfficial(userService.getLoginUser(request)));
    }

    /**
     * 题单详情和里面的题；别人的自建题单和收藏返回「不存在」
     */
    @GetMapping("/get")
    public BaseResponse<QuestionListDetailVO> getQuestionList(@RequestParam long id, HttpServletRequest request) {
        return ResultUtils.success(questionListService.getDetail(id, userService.getLoginUser(request)));
    }

    /**
     * 新建题单；official = true 建官方题单，只有管理员能用
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestionList(@RequestBody QuestionListAddRequest addRequest, HttpServletRequest request) {
        return ResultUtils.success(questionListService.addList(addRequest, userService.getLoginUser(request)));
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateQuestionList(@RequestBody QuestionListUpdateRequest updateRequest,
                                                    HttpServletRequest request) {
        return ResultUtils.success(questionListService.updateList(updateRequest, userService.getLoginUser(request)));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestionList(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(questionListService.deleteList(deleteRequest.getId(),
                userService.getLoginUser(request)));
    }

    @PostMapping("/question/add")
    public BaseResponse<Boolean> addQuestionToList(@RequestBody QuestionListItemRequest itemRequest,
                                                   HttpServletRequest request) {
        checkItem(itemRequest);
        return ResultUtils.success(questionListService.addQuestion(itemRequest.getListId(),
                itemRequest.getQuestionId(), userService.getLoginUser(request)));
    }

    @PostMapping("/question/remove")
    public BaseResponse<Boolean> removeQuestionFromList(@RequestBody QuestionListItemRequest itemRequest,
                                                        HttpServletRequest request) {
        checkItem(itemRequest);
        return ResultUtils.success(questionListService.removeQuestion(itemRequest.getListId(),
                itemRequest.getQuestionId(), userService.getLoginUser(request)));
    }

    /**
     * 题单里的题重新排序：传全部题目的 id，按新顺序
     */
    @PostMapping("/question/sort")
    public BaseResponse<Boolean> sortQuestionList(@RequestBody QuestionListSortRequest sortRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(sortRequest == null || sortRequest.getListId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(questionListService.sortQuestions(sortRequest.getListId(),
                sortRequest.getQuestionIds(), userService.getLoginUser(request)));
    }

    /**
     * 收藏 / 取消收藏一道题
     */
    @PostMapping("/favorite")
    public BaseResponse<Boolean> setQuestionFavorite(@RequestBody QuestionFavoriteRequest favoriteRequest,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(favoriteRequest == null || favoriteRequest.getQuestionId() == null
                || favoriteRequest.getFavorite() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(questionListService.setFavorite(favoriteRequest.getQuestionId(),
                favoriteRequest.getFavorite(), userService.getLoginUser(request)));
    }

    /**
     * 我收藏的全部题目 id
     */
    @GetMapping("/favorite/ids")
    public BaseResponse<List<Long>> listFavoriteQuestionIds(HttpServletRequest request) {
        return ResultUtils.success(questionListService.listFavoriteQuestionIds(userService.getLoginUser(request)));
    }

    /**
     * 我的题单里（含收藏），哪些包含这道题
     */
    @GetMapping("/containing")
    public BaseResponse<List<Long>> listMyListIdsContaining(@RequestParam long questionId, HttpServletRequest request) {
        return ResultUtils.success(questionListService.listMyListIdsContaining(questionId,
                userService.getLoginUser(request)));
    }

    private static void checkItem(QuestionListItemRequest itemRequest) {
        ThrowUtils.throwIf(itemRequest == null || itemRequest.getListId() == null
                || itemRequest.getQuestionId() == null, ErrorCode.PARAMS_ERROR);
    }
}
