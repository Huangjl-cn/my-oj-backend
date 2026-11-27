package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.mapper.QuestionListItemMapper;
import com.hjl.oj.mapper.QuestionListMapper;
import com.hjl.oj.model.dto.questionlist.QuestionListAddRequest;
import com.hjl.oj.model.dto.questionlist.QuestionListUpdateRequest;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionList;
import com.hjl.oj.model.entity.QuestionListItem;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.QuestionListTypeEnum;
import com.hjl.oj.model.vo.QuestionListDetailVO;
import com.hjl.oj.model.vo.QuestionListItemVO;
import com.hjl.oj.model.vo.QuestionListVO;
import com.hjl.oj.service.QuestionListService;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import com.hjl.oj.service.UserService;
import com.hjl.oj.utils.QuestionStats;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 题单：我的收藏、自建题单、官方题单。
 * 收藏也是一个题单（type = favorite），每人一个，第一次用到时建，uniqueKey 的唯一索引保证并发下也只有一个。
 */
@Service
public class QuestionListServiceImpl extends ServiceImpl<QuestionListMapper, QuestionList> implements QuestionListService {

    static final String FAVORITE_TITLE = "我的收藏";

    static final int MAX_TITLE_LENGTH = 40;

    static final int MAX_DESCRIPTION_LENGTH = 200;

    /**
     * 每人最多建几个自建题单（收藏不算）
     */
    static final int MAX_CUSTOM_LISTS = 50;

    /**
     * 一个题单最多几道题（收藏另算）
     */
    static final int MAX_LIST_QUESTIONS = 500;

    static final int MAX_FAVORITE_QUESTIONS = 2000;

    @Resource
    private QuestionListItemMapper questionListItemMapper;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private UserService userService;

    // region 查询

    @Override
    public List<QuestionListVO> listMine(User loginUser) {
        QuestionList favorite = getOrCreateFavorite(loginUser.getId());
        List<QuestionList> lists = new ArrayList<>();
        lists.add(favorite);
        lists.addAll(this.list(new QueryWrapper<QuestionList>()
                .eq("userId", loginUser.getId())
                .eq("type", QuestionListTypeEnum.CUSTOM.getValue())
                .orderByDesc("updateTime")));
        return toVOs(lists, loginUser);
    }

    @Override
    public List<QuestionListVO> listOfficial(User loginUser) {
        return toVOs(this.list(new QueryWrapper<QuestionList>()
                .eq("type", QuestionListTypeEnum.OFFICIAL.getValue())
                .orderByDesc("updateTime")), loginUser);
    }

    @Override
    public QuestionListDetailVO getDetail(long listId, User loginUser) {
        QuestionList list = getReadable(listId, loginUser);
        List<QuestionListItem> items = questionListItemMapper.selectActiveItems(List.of(list.getId()));
        Map<Long, Question> questionById = loadQuestions(items.stream().map(QuestionListItem::getQuestionId).toList());
        List<QuestionListItemVO> questions = new ArrayList<>();
        for (QuestionListItem item : items) {
            Question question = questionById.get(item.getQuestionId());
            if (question == null) {
                continue;
            }
            QuestionListItemVO vo = new QuestionListItemVO();
            vo.setQuestionId(question.getId());
            vo.setTitle(question.getTitle());
            vo.setTags(QuestionStats.parseTags(question.getTags()));
            vo.setSubmitNum(question.getSubmitNum());
            vo.setAcceptedNum(question.getAcceptedNum());
            vo.setAddTime(item.getCreateTime());
            questions.add(vo);
        }
        QuestionListDetailVO detail = new QuestionListDetailVO();
        detail.setList(toVOs(List.of(list), loginUser).getFirst());
        detail.setQuestions(questions);
        return detail;
    }

    @Override
    public List<Long> listFavoriteQuestionIds(User loginUser) {
        QuestionList favorite = findFavorite(loginUser.getId());
        if (favorite == null) {
            return List.of();
        }
        return questionListItemMapper.selectActiveItems(List.of(favorite.getId())).stream()
                .map(QuestionListItem::getQuestionId)
                .toList();
    }

    @Override
    public List<Long> listMyListIdsContaining(long questionId, User loginUser) {
        List<Long> myListIds = this.list(new QueryWrapper<QuestionList>()
                        .select("id")
                        .eq("userId", loginUser.getId())
                        .in("type", QuestionListTypeEnum.FAVORITE.getValue(), QuestionListTypeEnum.CUSTOM.getValue()))
                .stream().map(QuestionList::getId).toList();
        if (myListIds.isEmpty()) {
            return List.of();
        }
        return questionListItemMapper.selectList(new QueryWrapper<QuestionListItem>()
                        .select("listId")
                        .eq("questionId", questionId)
                        .in("listId", myListIds))
                .stream().map(QuestionListItem::getListId).toList();
    }

    // endregion

    // region 修改

    @Override
    public long addList(QuestionListAddRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean official = Boolean.TRUE.equals(request.getOfficial());
        ThrowUtils.throwIf(official && !userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "只有管理员能建官方题单");
        if (!official) {
            long count = this.count(new QueryWrapper<QuestionList>()
                    .eq("userId", loginUser.getId())
                    .eq("type", QuestionListTypeEnum.CUSTOM.getValue()));
            ThrowUtils.throwIf(count >= MAX_CUSTOM_LISTS, ErrorCode.OPERATION_ERROR,
                    "最多建 " + MAX_CUSTOM_LISTS + " 个题单");
        }
        QuestionList list = new QuestionList();
        list.setUserId(loginUser.getId());
        list.setTitle(checkTitle(request.getTitle()));
        list.setDescription(checkDescription(request.getDescription()));
        list.setType((official ? QuestionListTypeEnum.OFFICIAL : QuestionListTypeEnum.CUSTOM).getValue());
        ThrowUtils.throwIf(!this.save(list), ErrorCode.OPERATION_ERROR);
        return list.getId();
    }

    @Override
    public boolean updateList(QuestionListUpdateRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        QuestionList list = getEditable(request.getId(), loginUser);
        ThrowUtils.throwIf(isFavorite(list), ErrorCode.OPERATION_ERROR, "「我的收藏」不能改名");
        // 用 set 明确写入：简介清空时要写成 null，updateById 会跳过 null 字段
        return this.update(new UpdateWrapper<QuestionList>()
                .eq("id", list.getId())
                .set("title", checkTitle(request.getTitle()))
                .set("description", checkDescription(request.getDescription()))
                .set("updateTime", new Date()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteList(long listId, User loginUser) {
        QuestionList list = getEditable(listId, loginUser);
        ThrowUtils.throwIf(isFavorite(list), ErrorCode.OPERATION_ERROR, "「我的收藏」不能删除");
        questionListItemMapper.delete(new QueryWrapper<QuestionListItem>().eq("listId", list.getId()));
        return this.removeById(list.getId());
    }

    @Override
    public boolean addQuestion(long listId, long questionId, User loginUser) {
        return addTo(getEditable(listId, loginUser), questionId);
    }

    @Override
    public boolean removeQuestion(long listId, long questionId, User loginUser) {
        QuestionList list = getEditable(listId, loginUser);
        int removed = questionListItemMapper.delete(new QueryWrapper<QuestionListItem>()
                .eq("listId", list.getId())
                .eq("questionId", questionId));
        if (removed > 0) {
            touch(list.getId());
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sortQuestions(long listId, List<Long> questionIds, User loginUser) {
        ThrowUtils.throwIf(questionIds == null, ErrorCode.PARAMS_ERROR);
        QuestionList list = getEditable(listId, loginUser);
        List<QuestionListItem> items = questionListItemMapper.selectList(new QueryWrapper<QuestionListItem>()
                .eq("listId", list.getId()));
        Map<Long, QuestionListItem> itemByQuestion = items.stream()
                .collect(Collectors.toMap(QuestionListItem::getQuestionId, Function.identity(), (first, second) -> first));
        // 只排还没删的题；已删除的题（不在列表里显示）排到最后，保持原来的相对顺序
        Set<Long> given = new HashSet<>(questionIds);
        ThrowUtils.throwIf(given.size() != questionIds.size() || !itemByQuestion.keySet().containsAll(given),
                ErrorCode.PARAMS_ERROR, "题目和题单里的对不上，请刷新后再试");
        Set<Long> active = questionListItemMapper.selectActiveItems(List.of(list.getId())).stream()
                .map(QuestionListItem::getQuestionId).collect(Collectors.toSet());
        ThrowUtils.throwIf(!given.equals(active), ErrorCode.PARAMS_ERROR, "题目和题单里的对不上，请刷新后再试");
        List<Long> order = new ArrayList<>(questionIds);
        items.stream()
                .filter(item -> !given.contains(item.getQuestionId()))
                .sorted(Comparator.comparing(QuestionListItem::getSortOrder).thenComparing(QuestionListItem::getId))
                .forEach(item -> order.add(item.getQuestionId()));
        for (int index = 0; index < order.size(); index++) {
            QuestionListItem item = itemByQuestion.get(order.get(index));
            if (!Objects.equals(item.getSortOrder(), index)) {
                QuestionListItem update = new QuestionListItem();
                update.setId(item.getId());
                update.setSortOrder(index);
                questionListItemMapper.updateById(update);
            }
        }
        touch(list.getId());
        return true;
    }

    @Override
    public boolean setFavorite(long questionId, boolean favorite, User loginUser) {
        QuestionList list = getOrCreateFavorite(loginUser.getId());
        if (favorite) {
            return addTo(list, questionId);
        }
        questionListItemMapper.delete(new QueryWrapper<QuestionListItem>()
                .eq("listId", list.getId())
                .eq("questionId", questionId));
        return true;
    }

    // endregion

    // region 内部

    private boolean addTo(QuestionList list, long questionId) {
        Question question = questionService.getById(questionId);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        boolean exists = questionListItemMapper.selectCount(new QueryWrapper<QuestionListItem>()
                .eq("listId", list.getId())
                .eq("questionId", questionId)) > 0;
        if (exists) {
            return true;
        }
        List<QuestionListItem> items = questionListItemMapper.selectList(new QueryWrapper<QuestionListItem>()
                .select("sortOrder")
                .eq("listId", list.getId()));
        int limit = isFavorite(list) ? MAX_FAVORITE_QUESTIONS : MAX_LIST_QUESTIONS;
        ThrowUtils.throwIf(items.size() >= limit, ErrorCode.OPERATION_ERROR, "一个题单最多 " + limit + " 道题");
        QuestionListItem item = new QuestionListItem();
        item.setListId(list.getId());
        item.setQuestionId(questionId);
        item.setSortOrder(items.stream().map(QuestionListItem::getSortOrder).filter(Objects::nonNull)
                .max(Integer::compare).map(max -> max + 1).orElse(0));
        try {
            questionListItemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 同时点了两次，另一次已经加进去了
            return true;
        }
        touch(list.getId());
        return true;
    }

    private void touch(long listId) {
        QuestionList update = new QuestionList();
        update.setId(listId);
        update.setUpdateTime(new Date());
        this.updateById(update);
    }

    private QuestionList findFavorite(long userId) {
        return this.getOne(new QueryWrapper<QuestionList>()
                .eq("userId", userId)
                .eq("type", QuestionListTypeEnum.FAVORITE.getValue())
                .last("LIMIT 1"));
    }

    QuestionList getOrCreateFavorite(long userId) {
        QuestionList favorite = findFavorite(userId);
        if (favorite != null) {
            return favorite;
        }
        QuestionList list = new QuestionList();
        list.setUserId(userId);
        list.setTitle(FAVORITE_TITLE);
        list.setType(QuestionListTypeEnum.FAVORITE.getValue());
        list.setUniqueKey("favorite:" + userId);
        Date now = new Date();
        list.setCreateTime(now);
        list.setUpdateTime(now);
        try {
            this.save(list);
            return list;
        } catch (DuplicateKeyException e) {
            // 另一个请求刚建好
            QuestionList created = findFavorite(userId);
            ThrowUtils.throwIf(created == null, ErrorCode.SYSTEM_ERROR);
            return created;
        }
    }

    private static boolean isFavorite(QuestionList list) {
        return QuestionListTypeEnum.FAVORITE.getValue().equals(list.getType());
    }

    private static boolean isOfficial(QuestionList list) {
        return QuestionListTypeEnum.OFFICIAL.getValue().equals(list.getType());
    }

    private boolean isOwner(QuestionList list, User loginUser) {
        return Objects.equals(list.getUserId(), loginUser.getId());
    }

    /**
     * 能看：自己的题单，或官方题单；别人的当作不存在
     */
    private QuestionList getReadable(long listId, User loginUser) {
        QuestionList list = this.getById(listId);
        if (list == null || !(isOfficial(list) || isOwner(list, loginUser))) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题单不存在");
        }
        return list;
    }

    /**
     * 能改：自己的收藏和自建题单；官方题单只有管理员能改
     */
    private QuestionList getEditable(long listId, User loginUser) {
        QuestionList list = getReadable(listId, loginUser);
        ThrowUtils.throwIf(!canEdit(list, loginUser), ErrorCode.NO_AUTH_ERROR, "只有管理员能改官方题单");
        return list;
    }

    private boolean canEdit(QuestionList list, User loginUser) {
        return isOfficial(list) ? userService.isAdmin(loginUser) : isOwner(list, loginUser);
    }

    private static String checkTitle(String title) {
        String value = StringUtils.trimToEmpty(title);
        ThrowUtils.throwIf(value.isEmpty(), ErrorCode.PARAMS_ERROR, "题单标题不能为空");
        ThrowUtils.throwIf(value.length() > MAX_TITLE_LENGTH, ErrorCode.PARAMS_ERROR,
                "题单标题最多 " + MAX_TITLE_LENGTH + " 个字");
        return value;
    }

    private static String checkDescription(String description) {
        String value = StringUtils.trimToNull(description);
        ThrowUtils.throwIf(value != null && value.length() > MAX_DESCRIPTION_LENGTH, ErrorCode.PARAMS_ERROR,
                "简介最多 " + MAX_DESCRIPTION_LENGTH + " 个字");
        return value;
    }

    private Map<Long, Question> loadQuestions(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionService.list(new QueryWrapper<Question>()
                        .select("id", "title", "tags", "submitNum", "acceptedNum")
                        .in("id", ids))
                .stream()
                .collect(Collectors.toMap(Question::getId, Function.identity(), (first, second) -> first));
    }

    /**
     * 题单转成卡片：题目数、当前用户的通过数、能不能改。所有题单的题一次查出来，不逐个查
     */
    private List<QuestionListVO> toVOs(List<QuestionList> lists, User loginUser) {
        if (lists.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> questionsByList = new LinkedHashMap<>();
        for (QuestionListItem item : questionListItemMapper.selectActiveItems(
                lists.stream().map(QuestionList::getId).toList())) {
            questionsByList.computeIfAbsent(item.getListId(), key -> new ArrayList<>()).add(item.getQuestionId());
        }
        Set<Long> solved = new HashSet<>(questionSubmitService.listSolvedQuestionIds(loginUser.getId()));
        List<QuestionListVO> vos = new ArrayList<>();
        for (QuestionList list : lists) {
            List<Long> questionIds = questionsByList.getOrDefault(list.getId(), List.of());
            QuestionListVO vo = new QuestionListVO();
            vo.setId(list.getId());
            vo.setTitle(list.getTitle());
            vo.setDescription(list.getDescription());
            vo.setType(list.getType());
            vo.setQuestionCount(questionIds.size());
            vo.setSolvedCount((int) questionIds.stream().filter(solved::contains).count());
            vo.setEditable(canEdit(list, loginUser));
            vo.setUpdateTime(list.getUpdateTime());
            vos.add(vo);
        }
        return vos;
    }

    // endregion
}
