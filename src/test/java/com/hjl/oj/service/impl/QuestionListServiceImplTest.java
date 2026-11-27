package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.mapper.QuestionListItemMapper;
import com.hjl.oj.model.dto.questionlist.QuestionListAddRequest;
import com.hjl.oj.model.dto.questionlist.QuestionListUpdateRequest;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionList;
import com.hjl.oj.model.entity.QuestionListItem;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.QuestionListDetailVO;
import com.hjl.oj.model.vo.QuestionListVO;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import com.hjl.oj.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionListServiceImplTest {

    private static final long ME = 1L;

    private static final long OTHER = 2L;

    private final QuestionListItemMapper itemMapper = mock(QuestionListItemMapper.class);

    private final QuestionService questionService = mock(QuestionService.class);

    private final QuestionSubmitService questionSubmitService = mock(QuestionSubmitService.class);

    private final UserService userService = mock(UserService.class);

    private QuestionListServiceImpl service;

    private final User me = user(ME);

    @BeforeEach
    void setUp() {
        service = spy(new QuestionListServiceImpl());
        ReflectionTestUtils.setField(service, "questionListItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "questionService", questionService);
        ReflectionTestUtils.setField(service, "questionSubmitService", questionSubmitService);
        ReflectionTestUtils.setField(service, "userService", userService);
        doReturn(true).when(service).updateById(any(QuestionList.class));
        doReturn(true).when(service).removeById(any(Long.class));
        when(questionSubmitService.listSolvedQuestionIds(anyLong())).thenReturn(List.of());
    }

    // region 收藏

    @Test
    void favoriteListIsCreatedOnceWithItsUniqueKey() {
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doAnswer(invocation -> {
            invocation.<QuestionList>getArgument(0).setId(10L);
            return true;
        }).when(service).save(any(QuestionList.class));
        doReturn(List.of()).when(service).list(any(Wrapper.class));

        List<QuestionListVO> mine = service.listMine(me);

        ArgumentCaptor<QuestionList> saved = ArgumentCaptor.forClass(QuestionList.class);
        verify(service).save(saved.capture());
        assertEquals("favorite", saved.getValue().getType());
        assertEquals("favorite:1", saved.getValue().getUniqueKey());
        assertEquals("我的收藏", saved.getValue().getTitle());
        assertEquals(1, mine.size());
        assertEquals(10L, mine.getFirst().getId());
        assertTrue(mine.getFirst().getEditable());
    }

    @Test
    void concurrentFirstFavoriteKeepsTheOneThatWasCreatedFirst() {
        QuestionList existing = list(11L, ME, "favorite");
        doReturn(null, existing).when(service).getOne(any(Wrapper.class));
        doThrow(new DuplicateKeyException("uk_unique_key")).when(service).save(any(QuestionList.class));

        assertEquals(11L, service.getOrCreateFavorite(ME).getId());
    }

    @Test
    void favoriteCannotBeRenamedOrDeleted() {
        doReturn(list(11L, ME, "favorite")).when(service).getById(11L);
        QuestionListUpdateRequest rename = new QuestionListUpdateRequest();
        rename.setId(11L);
        rename.setTitle("改个名");

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.updateList(rename, me)).getCode());
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.deleteList(11L, me)).getCode());
        verify(service, never()).removeById(any(Long.class));
    }

    @Test
    void unfavoriteRemovesTheQuestionFromTheFavoriteList() {
        doReturn(list(11L, ME, "favorite")).when(service).getOne(any(Wrapper.class));

        assertTrue(service.setFavorite(5L, false, me));
        verify(itemMapper).delete(any(Wrapper.class));
        verify(itemMapper, never()).insert(any(QuestionListItem.class));
    }

    // endregion

    // region 谁能看、谁能改

    @Test
    void otherPeoplesListsLookLikeTheyDoNotExist() {
        doReturn(list(20L, OTHER, "custom")).when(service).getById(20L);
        doReturn(list(21L, OTHER, "favorite")).when(service).getById(21L);

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.getDetail(20L, me)).getCode());
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addQuestion(21L, 5L, me)).getCode());
    }

    @Test
    void officialListsAreReadableByEveryoneButEditableOnlyByAdmins() {
        doReturn(list(30L, OTHER, "official")).when(service).getById(30L);
        when(itemMapper.selectActiveItems(any())).thenReturn(List.of());
        when(userService.isAdmin(me)).thenReturn(false);

        QuestionListDetailVO detail = service.getDetail(30L, me);
        assertFalse(detail.getList().getEditable());
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addQuestion(30L, 5L, me)).getCode());

        when(userService.isAdmin(me)).thenReturn(true);
        assertTrue(service.getDetail(30L, me).getList().getEditable());
    }

    @Test
    void onlyAdminsCanCreateOfficialLists() {
        when(userService.isAdmin(me)).thenReturn(false);
        QuestionListAddRequest request = new QuestionListAddRequest();
        request.setTitle("入门 30 题");
        request.setOfficial(true);

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addList(request, me)).getCode());
        verify(service, never()).save(any(QuestionList.class));
    }

    @Test
    void titleIsRequiredAndLengthsAreChecked() {
        doReturn(0L).when(service).count(any(Wrapper.class));
        QuestionListAddRequest blank = new QuestionListAddRequest();
        blank.setTitle("   ");
        QuestionListAddRequest tooLong = new QuestionListAddRequest();
        tooLong.setTitle("题".repeat(41));
        QuestionListAddRequest longDescription = new QuestionListAddRequest();
        longDescription.setTitle("动态规划");
        longDescription.setDescription("说".repeat(201));

        for (QuestionListAddRequest request : List.of(blank, tooLong, longDescription)) {
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                    assertThrows(BusinessException.class, () -> service.addList(request, me)).getCode());
        }
    }

    @Test
    void clearingTheDescriptionWritesNull() {
        doReturn(list(40L, ME, "custom")).when(service).getById(40L);
        doReturn(true).when(service).update(any(Wrapper.class));
        QuestionListUpdateRequest request = new QuestionListUpdateRequest();
        request.setId(40L);
        request.setTitle(" 新名字 ");
        request.setDescription("   ");

        assertTrue(service.updateList(request, me));

        ArgumentCaptor<UpdateWrapper<QuestionList>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(service).update(captor.capture());
        // 三项都明确写入（含清空成 null 的简介），不会因为是 null 被跳过
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("title=") && sqlSet.contains("description=") && sqlSet.contains("updateTime="));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("新名字"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(null));
    }

    @Test
    void customListsAreLimitedPerUser() {
        doReturn(50L).when(service).count(any(Wrapper.class));
        QuestionListAddRequest request = new QuestionListAddRequest();
        request.setTitle("第 51 个");

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addList(request, me)).getCode());
    }

    // endregion

    // region 加题、排序

    @Test
    void addingAQuestionAppendsItToTheEnd() {
        doReturn(list(40L, ME, "custom")).when(service).getById(40L);
        when(questionService.getById(5L)).thenReturn(question(5L));
        when(itemMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(1L, 3), item(2L, 7)));

        assertTrue(service.addQuestion(40L, 5L, me));

        ArgumentCaptor<QuestionListItem> inserted = ArgumentCaptor.forClass(QuestionListItem.class);
        verify(itemMapper).insert(inserted.capture());
        assertEquals(40L, inserted.getValue().getListId());
        assertEquals(5L, inserted.getValue().getQuestionId());
        assertEquals(8, inserted.getValue().getSortOrder());
    }

    @Test
    void addingTheSameQuestionTwiceDoesNothing() {
        doReturn(list(40L, ME, "custom")).when(service).getById(40L);
        when(questionService.getById(5L)).thenReturn(question(5L));
        when(itemMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertTrue(service.addQuestion(40L, 5L, me));
        verify(itemMapper, never()).insert(any(QuestionListItem.class));
    }

    @Test
    void deletedQuestionsCannotBeAdded() {
        doReturn(list(40L, ME, "custom")).when(service).getById(40L);
        when(questionService.getById(5L)).thenReturn(null);

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addQuestion(40L, 5L, me)).getCode());
    }

    @Test
    void aListHoldsAtMost500Questions() {
        doReturn(list(40L, ME, "custom")).when(service).getById(40L);
        when(questionService.getById(5L)).thenReturn(question(5L));
        when(itemMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        List<QuestionListItem> full = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            full.add(item(100L + i, i));
        }
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(full);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addQuestion(40L, 5L, me)).getCode());
    }

    @Test
    void sortingNeedsExactlyTheQuestionsInTheList() {
        doReturn(list(40L, ME, "custom")).when(service).getById(40L);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(1L, 0), item(2L, 1), item(3L, 2)));
        when(itemMapper.selectActiveItems(any())).thenReturn(List.of(item(1L, 0), item(2L, 1), item(3L, 2)));

        for (List<Long> wrong : List.of(List.of(1L, 2L), List.of(1L, 2L, 9L), List.of(1L, 1L, 2L))) {
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                    assertThrows(BusinessException.class, () -> service.sortQuestions(40L, wrong, me)).getCode());
        }

        assertTrue(service.sortQuestions(40L, List.of(3L, 1L, 2L), me));
        // 3 从 2 挪到 0，1 从 0 挪到 1，2 从 1 挪到 2：三条都要更新
        verify(itemMapper, times(3)).updateById(any(QuestionListItem.class));
    }

    // endregion

    @Test
    void cardsCountOnlyExistingQuestionsAndMySolvedOnes() {
        QuestionList custom = list(40L, ME, "custom");
        doReturn(list(11L, ME, "favorite")).when(service).getOne(any(Wrapper.class));
        doReturn(List.of(custom)).when(service).list(any(Wrapper.class));
        // selectActiveItems 已经去掉了删除的题
        when(itemMapper.selectActiveItems(any())).thenReturn(List.of(
                listItem(11L, 1L), listItem(40L, 1L), listItem(40L, 2L), listItem(40L, 3L)));
        when(questionSubmitService.listSolvedQuestionIds(ME)).thenReturn(List.of(1L, 3L));

        List<QuestionListVO> mine = service.listMine(me);

        assertEquals(List.of(11L, 40L), mine.stream().map(QuestionListVO::getId).toList());
        assertEquals(1, mine.get(0).getQuestionCount());
        assertEquals(1, mine.get(0).getSolvedCount());
        assertEquals(3, mine.get(1).getQuestionCount());
        assertEquals(2, mine.get(1).getSolvedCount());
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static QuestionList list(long id, long userId, String type) {
        QuestionList list = new QuestionList();
        list.setId(id);
        list.setUserId(userId);
        list.setType(type);
        list.setTitle("题单" + id);
        return list;
    }

    private static Question question(long id) {
        Question question = new Question();
        question.setId(id);
        return question;
    }

    private static QuestionListItem item(long questionId, int sortOrder) {
        QuestionListItem item = new QuestionListItem();
        item.setId(1000L + questionId);
        item.setListId(40L);
        item.setQuestionId(questionId);
        item.setSortOrder(sortOrder);
        return item;
    }

    private static QuestionListItem listItem(long listId, long questionId) {
        QuestionListItem item = new QuestionListItem();
        item.setListId(listId);
        item.setQuestionId(questionId);
        return item;
    }
}
