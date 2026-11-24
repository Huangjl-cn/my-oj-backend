package com.hjl.oj.service.impl;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.ai.call.AiCallGuard;
import com.hjl.oj.ai.config.AiMode;
import com.hjl.oj.ai.config.AiProperties;
import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.companion.assistant.CompanionAssistantFactory;
import com.hjl.oj.companion.assistant.CompanionChatAssistant;
import com.hjl.oj.companion.memory.MemoryCompactor;
import com.hjl.oj.companion.memory.MysqlChatMemoryStore;
import com.hjl.oj.companion.prompt.CompanionPrompts;
import com.hjl.oj.companion.tool.CodeEditTools;
import com.hjl.oj.companion.tool.CodeRewriteTools;
import com.hjl.oj.companion.tool.SubmissionHistoryTools;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.dto.companion.CodeSnapshot;
import com.hjl.oj.model.dto.companion.CompanionChatRequest;
import com.hjl.oj.model.dto.companion.CompanionHintRequest;
import com.hjl.oj.model.dto.companion.CompanionLevelRequest;
import com.hjl.oj.model.dto.companion.CompanionReactRequest;
import com.hjl.oj.model.entity.CompanionMessage;
import com.hjl.oj.model.entity.CompanionSession;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.CompanionMessageTypeEnum;
import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.model.vo.CompanionReactionVO;
import com.hjl.oj.service.AiSettingService;
import com.hjl.oj.service.CompanionMessageService;
import com.hjl.oj.service.CompanionSessionService;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionServiceImplTest {

    private static final long USER_ID = 1L;

    private static final long QUESTION_ID = 2L;

    private final QuestionService questionService = mock(QuestionService.class);

    private final QuestionSubmitService questionSubmitService = mock(QuestionSubmitService.class);

    private final AiSettingService aiSettingService = mock(AiSettingService.class);

    private final CompanionSessionService sessionService = mock(CompanionSessionService.class);

    private final CompanionMessageService messageService = mock(CompanionMessageService.class);

    private final CompanionAssistantFactory assistantFactory = mock(CompanionAssistantFactory.class);

    private final CompanionPrompts prompts = mock(CompanionPrompts.class);

    private final MysqlChatMemoryStore memoryStore = mock(MysqlChatMemoryStore.class);

    private final AiCallGuard aiCallGuard = new AiCallGuard();

    private CompanionServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setMode(AiMode.MOCK);
        properties.getCompanion().setPersonaName("栗子");
        service = new CompanionServiceImpl(questionService, questionSubmitService, aiSettingService, sessionService,
                messageService, assistantFactory, prompts, mock(MemoryCompactor.class), memoryStore, aiCallGuard,
                properties);
        user = new User();
        user.setId(USER_ID);
        Question question = new Question();
        question.setId(QUESTION_ID);
        when(questionService.getById(QUESTION_ID)).thenReturn(question);
        CompanionSession session = new CompanionSession();
        session.setId(9L);
        session.setUserId(USER_ID);
        session.setQuestionId(QUESTION_ID);
        session.setHintLevel(0);
        session.setHintTrack("idea");
        session.setContextTokens(0);
        when(sessionService.getOrCreate(USER_ID, QUESTION_ID)).thenReturn(session);
        when(aiSettingService.getHintMode(USER_ID)).thenReturn(HintModeEnum.GUIDED);
        when(aiSettingService.getContextWindow(USER_ID)).thenReturn(1_000_000);
        when(aiSettingService.requireCredential(USER_ID)).thenReturn(new AiCredential(null, "deepseek-flash"));
        when(prompts.systemMessage(any())).thenReturn("system");
        when(memoryStore.getMessages(any())).thenReturn(List.of());
    }

    @Test
    void reactRejectsSomeoneElsesSubmission() {
        when(questionSubmitService.getById(5L)).thenReturn(submission(5L, 99L, "Accepted"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.react(user, reactRequest(5L)));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), error.getCode());
    }

    @Test
    void reactRejectsSubmissionStillJudging() {
        QuestionSubmit judging = submission(5L, USER_ID, "{}");
        judging.setStatus(QuestionSubmitStatusEnum.RUNNING.getValue());
        when(questionSubmitService.getById(5L)).thenReturn(judging);

        BusinessException error = assertThrows(BusinessException.class, () -> service.react(user, reactRequest(5L)));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), error.getCode());
    }

    @Test
    void reactIsIdempotentPerSubmission() {
        when(questionSubmitService.getById(5L)).thenReturn(submission(5L, USER_ID, "Accepted"));
        CompanionMessage existing = new CompanionMessage();
        existing.setId(77L);
        existing.setSubmissionId(5L);
        existing.setMood("proud");
        existing.setContent("上次说过的话");
        when(messageService.findReaction(USER_ID, 5L)).thenReturn(existing);

        CompanionReactionVO vo = service.react(user, reactRequest(5L));

        assertEquals(77L, vo.getMessageId());
        assertEquals("上次说过的话", vo.getMessage());
        verify(assistantFactory, never()).reactionAssistant(any());
    }

    @Test
    void reactFallsBackWhenModelCannotBeCalled() {
        when(questionSubmitService.getById(5L)).thenReturn(submission(5L, USER_ID, "Wrong Answer\n输入：[1]"));
        when(aiSettingService.canCallModel(USER_ID)).thenReturn(false);
        when(messageService.recentReactionLines(USER_ID, QUESTION_ID, CompanionServiceImpl.RECENT_REACTION_LINES))
                .thenReturn(List.of());

        CompanionReactionVO vo = service.react(user, reactRequest(5L));

        assertTrue(vo.getFallback());
        assertFalse(vo.getMessage().isBlank());
        ArgumentCaptor<CompanionMessage> saved = ArgumentCaptor.forClass(CompanionMessage.class);
        verify(messageService).saveMessage(saved.capture(), eq(null));
        assertEquals(CompanionMessageTypeEnum.REACTION.getValue(), saved.getValue().getType());
        assertEquals(5L, saved.getValue().getSubmissionId());
        verify(assistantFactory, never()).reactionAssistant(any());
    }

    @Test
    void hintRejectsSolutionLevelAndReleasesPermit() {
        CompanionHintRequest request = new CompanionHintRequest();
        request.setQuestionId(QUESTION_ID);
        request.setTargetLevel(5);

        assertThrows(BusinessException.class, () -> service.hint(user, request));
        // 许可已释放：同一用户可以立刻再次占用
        aiCallGuard.acquire(USER_ID).close();
    }

    @Test
    void setLevelOnlyAcceptsZeroToFour() {
        CompanionLevelRequest request = new CompanionLevelRequest();
        request.setQuestionId(QUESTION_ID);
        request.setLevel(5);

        assertThrows(BusinessException.class, () -> service.setLevel(user, request));

        request.setLevel(0);
        assertEquals(0, service.setLevel(user, request).getHintLevel());
        verify(sessionService).updateHintState(eq(9L), eq(0), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatOffersRewriteToolOnlyAtUnrestrictedLevel() {
        CompanionChatAssistant chatAssistant = mock(CompanionChatAssistant.class);
        when(chatAssistant.chat(anyString(), anyString())).thenAnswer(invocation -> new CompletingTokenStream());
        when(assistantFactory.chatAssistant(any(), any(), any())).thenReturn(chatAssistant);
        when(prompts.chatMessage(any(), any(), anyString(), anyString())).thenReturn("prompt");
        when(messageService.reactionsSince(anyLong(), anyLong(), any(), anyInt())).thenReturn(List.of());

        service.chat(user, chatRequest());
        when(aiSettingService.getHintMode(USER_ID)).thenReturn(HintModeEnum.DIRECT);
        service.chat(user, chatRequest());

        ArgumentCaptor<List<Object>> tools = ArgumentCaptor.forClass(List.class);
        verify(assistantFactory, times(2)).chatAssistant(any(), any(), tools.capture());
        List<Object> guided = tools.getAllValues().get(0);
        List<Object> direct = tools.getAllValues().get(1);
        assertTrue(guided.stream().anyMatch(SubmissionHistoryTools.class::isInstance));
        assertTrue(guided.stream().anyMatch(CodeEditTools.class::isInstance));
        assertFalse(guided.stream().anyMatch(CodeRewriteTools.class::isInstance));
        assertTrue(direct.stream().anyMatch(CodeRewriteTools.class::isInstance));
        // 流式回答收尾：陪练的回答落库、上下文占用更新
        verify(sessionService, times(2)).updateContextTokens(eq(9L), eq(1500));
    }

    @Test
    void chatRejectsSecondConcurrentRequestOfSameUser() {
        AiCallGuard.Permit busy = aiCallGuard.acquire(USER_ID);
        try {
            BusinessException error = assertThrows(BusinessException.class, () -> service.chat(user, chatRequest()));
            assertEquals(ErrorCode.AI_BUSY.getCode(), error.getCode());
        } finally {
            busy.close();
        }
    }

    @Test
    void contextTokensComeFromTheLastModelCall() {
        ChatResponse total = ChatResponse.builder()
                .aiMessage(AiMessage.from("done"))
                .tokenUsage(new TokenUsage(1000, 200))
                .build();

        assertEquals(700 + 150, CompanionServiceImpl.lastCallContextTokens(total, 300, 50));
    }

    /**
     * 启动后立即以完整回答结束的 TokenStream，模拟一次没有工具调用的流式回答
     */
    private static final class CompletingTokenStream implements TokenStream {

        private Consumer<String> partialHandler = text -> {
        };

        private Consumer<ChatResponse> completeHandler = response -> {
        };

        @Override
        public TokenStream onPartialResponse(Consumer<String> handler) {
            this.partialHandler = handler;
            return this;
        }

        @Override
        public TokenStream onPartialThinking(Consumer<PartialThinking> handler) {
            return this;
        }

        @Override
        public TokenStream onIntermediateResponse(Consumer<ChatResponse> handler) {
            return this;
        }

        @Override
        public TokenStream onRetrieved(Consumer<List<Content>> handler) {
            return this;
        }

        @Override
        public TokenStream onToolExecuted(Consumer<ToolExecution> handler) {
            return this;
        }

        @Override
        public TokenStream onCompleteResponse(Consumer<ChatResponse> handler) {
            this.completeHandler = handler;
            return this;
        }

        @Override
        public TokenStream onError(Consumer<Throwable> handler) {
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            return this;
        }

        @Override
        public void start() {
            partialHandler.accept("看第 1 行");
            completeHandler.accept(ChatResponse.builder()
                    .aiMessage(AiMessage.from("看第 1 行"))
                    .tokenUsage(new TokenUsage(1200, 300))
                    .build());
        }
    }

    private static CompanionChatRequest chatRequest() {
        CodeSnapshot snapshot = new CodeSnapshot();
        snapshot.setCode("int a = 1;");
        snapshot.setLanguage("java");
        CompanionChatRequest request = new CompanionChatRequest();
        request.setQuestionId(QUESTION_ID);
        request.setMessage("帮我看看");
        request.setSnapshot(snapshot);
        return request;
    }

    private static CompanionReactRequest reactRequest(long submissionId) {
        CompanionReactRequest request = new CompanionReactRequest();
        request.setSubmissionId(submissionId);
        return request;
    }

    private static QuestionSubmit submission(long id, long userId, String message) {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(message);
        QuestionSubmit submit = new QuestionSubmit();
        submit.setId(id);
        submit.setUserId(userId);
        submit.setQuestionId(QUESTION_ID);
        submit.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        submit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        return submit;
    }
}
