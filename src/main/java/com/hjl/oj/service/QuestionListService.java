package com.hjl.oj.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.dto.questionlist.QuestionListAddRequest;
import com.hjl.oj.model.dto.questionlist.QuestionListUpdateRequest;
import com.hjl.oj.model.entity.QuestionList;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.QuestionListDetailVO;
import com.hjl.oj.model.vo.QuestionListVO;

import java.util.List;

/**
 * 题单：我的收藏、自建题单、官方题单
 */
public interface QuestionListService extends IService<QuestionList> {

    /**
     * 我的题单：「我的收藏」在最前（没有就先建），其余按最近修改排
     */
    List<QuestionListVO> listMine(User loginUser);

    /**
     * 官方题单，按最近修改排
     */
    List<QuestionListVO> listOfficial(User loginUser);

    /**
     * 题单详情；别人的自建题单和收藏当作不存在
     */
    QuestionListDetailVO getDetail(long listId, User loginUser);

    long addList(QuestionListAddRequest request, User loginUser);

    boolean updateList(QuestionListUpdateRequest request, User loginUser);

    boolean deleteList(long listId, User loginUser);

    /**
     * 加一道题到题单末尾；已经在里面就什么也不做
     */
    boolean addQuestion(long listId, long questionId, User loginUser);

    boolean removeQuestion(long listId, long questionId, User loginUser);

    /**
     * 按给出的顺序重排；questionIds 必须正好是题单里现有的题
     */
    boolean sortQuestions(long listId, List<Long> questionIds, User loginUser);

    /**
     * 收藏 / 取消收藏一道题（重复调用结果一样）
     */
    boolean setFavorite(long questionId, boolean favorite, User loginUser);

    /**
     * 我收藏的全部题目 id（题库列表一次取全，用来画星标）
     */
    List<Long> listFavoriteQuestionIds(User loginUser);

    /**
     * 我能改的题单里，哪些包含这道题（「加入题单」菜单里打勾用）
     */
    List<Long> listMyListIdsContaining(long questionId, User loginUser);
}
