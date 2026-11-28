package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hjl.oj.ai.call.AiCallGuard;
import com.hjl.oj.ai.call.AiErrorTranslator;
import com.hjl.oj.ai.config.AiMode;
import com.hjl.oj.ai.config.AiProperties;
import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.companion.assistant.CompanionAssistantFactory;
import com.hjl.oj.companion.assistant.HintAnswer;
import com.hjl.oj.companion.assistant.HintEditAnswer;
import com.hjl.oj.companion.assistant.ReactionAnswer;
import com.hjl.oj.companion.context.SubmissionFacts;
import com.hjl.oj.companion.context.SubmissionFactsBuilder;
import com.hjl.oj.companion.context.Verdict;
import com.hjl.oj.companion.context.VerdictClassifier;
import com.hjl.oj.companion.edit.CodeEditProposal;
import com.hjl.oj.companion.edit.CodeEditSession;
import com.hjl.oj.companion.hint.EditPermission;
import com.hjl.oj.companion.hint.HintLadder;
import com.hjl.oj.companion.hint.HintState;
import com.hjl.oj.companion.memory.ConversationMemoryId;
import com.hjl.oj.companion.memory.MemoryCompactor;
import com.hjl.oj.companion.memory.MemoryTranscript;
import com.hjl.oj.companion.memory.MysqlChatMemoryStore;
import com.hjl.oj.companion.note.NoteEditSession;
import com.hjl.oj.companion.note.NoteProposal;
import com.hjl.oj.companion.prompt.CompanionPrompts;
import com.hjl.oj.companion.reaction.FallbackReactions;
import com.hjl.oj.companion.stream.CompanionSse;
import com.hjl.oj.companion.tool.CodeEditTools;
import com.hjl.oj.companion.tool.CodeRewriteTools;
import com.hjl.oj.companion.tool.NoteTools;
import com.hjl.oj.companion.trace.TraceRecorder;
import com.hjl.oj.companion.tool.SubmissionHistoryTools;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.companion.CodeQuote;
import com.hjl.oj.model.dto.companion.CodeSnapshot;
import com.hjl.oj.model.dto.companion.CompanionChatRequest;
import com.hjl.oj.model.dto.companion.CompanionCompactRequest;
import com.hjl.oj.model.dto.companion.CompanionDeleteConversationRequest;
import com.hjl.oj.model.dto.companion.CompanionHintRequest;
import com.hjl.oj.model.dto.companion.CompanionLevelRequest;
import com.hjl.oj.model.dto.companion.CompanionNewConversationRequest;
import com.hjl.oj.model.dto.companion.CompanionReactRequest;
import com.hjl.oj.model.dto.companion.CompanionSolutionRequest;
import com.hjl.oj.model.dto.companion.CompanionSwitchConversationRequest;
import com.hjl.oj.model.entity.CompanionConversation;
import com.hjl.oj.model.entity.CompanionMessage;
import com.hjl.oj.model.entity.CompanionSession;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.CompanionMessageTypeEnum;
import com.hjl.oj.model.enums.CompanionMoodEnum;
import com.hjl.oj.model.enums.CompanionRoleEnum;
import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.HintTrackEnum;
import com.hjl.oj.model.vo.CompanionCompactionVO;
import com.hjl.oj.model.vo.CompanionConversationVO;
import com.hjl.oj.model.vo.CompanionHintStateVO;
import com.hjl.oj.model.vo.CompanionHintVO;
import com.hjl.oj.model.vo.CompanionMessageVO;
import com.hjl.oj.model.vo.CompanionReactionVO;
import com.hjl.oj.model.vo.CompanionSessionVO;
import com.hjl.oj.model.vo.CompanionStreamDoneVO;
import com.hjl.oj.model.vo.ContextUsageVO;
import com.hjl.oj.model.vo.HintStepVO;
import com.hjl.oj.service.AiSettingService;
import com.hjl.oj.service.CompanionConversationService;
import com.hjl.oj.service.CompanionMessageService;
import com.hjl.oj.service.CompanionService;
import com.hjl.oj.service.CompanionSessionService;
import com.hjl.oj.service.CompanionTraceService;
import com.hjl.oj.service.NoteService;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 陪练编排。每个调用模型的流程都是：校验 → 取凭证 → 占用调用许可 → 必要时自动压缩记忆 → 调用模型 → 落库，
 * 任何一步失败都释放许可。
 */
@Slf4j
@Service
public class CompanionServiceImpl implements CompanionService {

    static final long STREAM_TIMEOUT_MILLIS = 5 * 60 * 1000L;

    static final int MAX_MESSAGE_CHARS = 2000;

    static final int MAX_REQUEST_CODE_CHARS = 100_000;

    /**
     * 不挂记忆的调用（提示、反应）里，对话回顾的最大字符数
     */
    static final int TRANSCRIPT_CHARS = 6000;

    static final int RECENT_REACTION_LINES = 3;

    static final int RECENT_EVENT_LIMIT = 5;

    static final int PREVIOUS_SUBMISSION_LIMIT = 20;

    static final int USER_QUESTION_LIMIT = 200;

    static final int MAX_QUOTE_CHARS = 4000;

    private static final Pattern CODE_BLOCK = Pattern.compile("```[\\w+#-]*\\n(.*?)```", Pattern.DOTALL);

    private static final DateTimeFormatter EVENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private static final String HINT_MEMORY_NOTE = "【场景】用户点了「给点提示」，当时的档位是第 %d 级。";

    private static final String SOLUTION_USER_TEXT = "我想看答案";

    private final QuestionService questionService;

    private final QuestionSubmitService questionSubmitService;

    private final AiSettingService aiSettingService;

    private final CompanionSessionService companionSessionService;

    private final CompanionConversationService companionConversationService;

    private final CompanionMessageService companionMessageService;

    private final CompanionAssistantFactory assistantFactory;

    private final CompanionPrompts companionPrompts;

    private final MemoryCompactor memoryCompactor;

    private final MysqlChatMemoryStore chatMemoryStore;

    private final AiCallGuard aiCallGuard;

    private final AiProperties aiProperties;

    private final NoteService noteService;

    private final CompanionTraceService companionTraceService;

    public CompanionServiceImpl(QuestionService questionService, QuestionSubmitService questionSubmitService,
                                AiSettingService aiSettingService, CompanionSessionService companionSessionService,
                                CompanionConversationService companionConversationService,
                                CompanionMessageService companionMessageService,
                                CompanionAssistantFactory assistantFactory, CompanionPrompts companionPrompts,
                                MemoryCompactor memoryCompactor, MysqlChatMemoryStore chatMemoryStore,
                                AiCallGuard aiCallGuard, AiProperties aiProperties, NoteService noteService,
                                CompanionTraceService companionTraceService) {
        this.questionService = questionService;
        this.questionSubmitService = questionSubmitService;
        this.aiSettingService = aiSettingService;
        this.companionSessionService = companionSessionService;
        this.companionConversationService = companionConversationService;
        this.companionMessageService = companionMessageService;
        this.assistantFactory = assistantFactory;
        this.companionPrompts = companionPrompts;
        this.memoryCompactor = memoryCompactor;
        this.chatMemoryStore = chatMemoryStore;
        this.aiCallGuard = aiCallGuard;
        this.aiProperties = aiProperties;
        this.noteService = noteService;
        this.companionTraceService = companionTraceService;
    }

    // region 会话与对话

    @Override
    public CompanionSessionVO getSession(User loginUser, long questionId) {
        requireQuestion(questionId);
        CompanionSession session = companionSessionService.getOrCreate(loginUser.getId(), questionId);
        return sessionVO(session, companionConversationService.getActive(session));
    }

    @Override
    public List<CompanionMessageVO> listMessages(User loginUser, long conversationId, Long beforeId) {
        companionConversationService.requireOwned(loginUser.getId(), conversationId);
        int pageSize = aiProperties.getCompanion().getHistoryPageSize();
        return companionMessageService.listLatest(conversationId, beforeId, pageSize).stream()
                .map(companionMessageService::toVO)
                .toList();
    }

    @Override
    public List<CompanionConversationVO> listConversations(User loginUser, long questionId) {
        requireQuestion(questionId);
        CompanionSession session = companionSessionService.getOrCreate(loginUser.getId(), questionId);
        companionConversationService.getActive(session);
        return companionConversationService.listForQuestion(session);
    }

    @Override
    public CompanionSessionVO newConversation(User loginUser, CompanionNewConversationRequest request) {
        if (request == null || request.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        requireQuestion(request.getQuestionId());
        CompanionSession session = companionSessionService.getOrCreate(loginUser.getId(), request.getQuestionId());
        return sessionVO(session, companionConversationService.startNew(session));
    }

    @Override
    public CompanionSessionVO switchConversation(User loginUser, CompanionSwitchConversationRequest request) {
        if (request == null || request.getConversationId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Target target = target(loginUser.getId(), request.getConversationId());
        return sessionVO(target.session(), target.conversation());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanionSessionVO deleteConversation(User loginUser, CompanionDeleteConversationRequest request) {
        if (request == null || request.getConversationId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long userId = loginUser.getId();
        CompanionConversation conversation = companionConversationService.requireOwned(userId,
                request.getConversationId());
        // 鸭鸭正在回答时不删：回答结束还要往这段对话里写消息
        try (AiCallGuard.Permit ignored = aiCallGuard.acquire(userId)) {
            CompanionSession session = companionSessionService.getOrCreate(userId, conversation.getQuestionId());
            long id = conversation.getId();
            companionMessageService.remove(new QueryWrapper<CompanionMessage>().eq("conversationId", id));
            companionTraceService.deleteByConversation(id);
            companionConversationService.removeById(id);
            CompanionConversation active = Objects.equals(session.getActiveConversationId(), id)
                    ? companionConversationService.activateLatest(session)
                    : companionConversationService.getActive(session);
            return sessionVO(session, active);
        }
    }

    private CompanionSessionVO sessionVO(CompanionSession session, CompanionConversation conversation) {
        long userId = session.getUserId();
        HintState state = hintState(session, aiSettingService.getHintMode(userId));
        int pageSize = aiProperties.getCompanion().getHistoryPageSize();
        List<CompanionMessage> messages = companionMessageService.listLatest(conversation.getId(), null, pageSize + 1);
        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = messages.subList(1, messages.size());
        }

        CompanionSessionVO vo = new CompanionSessionVO();
        vo.setQuestionId(session.getQuestionId());
        vo.setConversationId(conversation.getId());
        vo.setHintLevel(state.unlockedLevel());
        vo.setEffectiveLevel(state.effectiveLevel());
        vo.setHintMode(state.mode().getValue());
        vo.setHintSteps(HintLadder.steps().stream()
                .map(step -> new HintStepVO(step.level(), step.title(), step.description()))
                .toList());
        vo.setCanCallModel(aiSettingService.canCallModel(userId));
        vo.setMockMode(aiProperties.getMode() == AiMode.MOCK);
        vo.setPersonaName(aiProperties.getCompanion().getPersonaName());
        vo.setContextUsage(contextUsage(userId, conversation.getContextTokens(), false));
        vo.setMessages(messages.stream().map(companionMessageService::toVO).toList());
        vo.setHasMoreMessages(hasMore);
        return vo;
    }

    // endregion

    // region 提交后反应

    @Override
    public CompanionReactionVO react(User loginUser, CompanionReactRequest request) {
        if (request == null || request.getSubmissionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long userId = loginUser.getId();
        QuestionSubmit submission = questionSubmitService.getById(request.getSubmissionId());
        if (submission == null || !Long.valueOf(userId).equals(submission.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        Verdict verdict = VerdictClassifier.classify(submission);
        if (!verdict.terminal()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "还在判题中，结果出来后再调用");
        }
        CompanionMessage existing = companionMessageService.findReaction(userId, submission.getId());
        if (existing != null) {
            return reactionVO(existing, false);
        }

        long questionId = submission.getQuestionId();
        Question question = requireQuestion(questionId);
        CompanionSession session = companionSessionService.getOrCreate(userId, questionId);
        CompanionConversation conversation = companionConversationService.getActive(session);
        HintState state = hintState(session, aiSettingService.getHintMode(userId));
        List<String> recentLines = companionMessageService.recentReactionLines(userId, questionId, RECENT_REACTION_LINES);

        ReactionAnswer answer = null;
        TraceRecorder trace = null;
        RuntimeException failure = null;
        if (aiSettingService.canCallModel(userId)) {
            trace = companionTraceService.startTurn(userId, questionId, conversation.getId(),
                    CompanionMessageTypeEnum.REACTION.getValue(), "提交了代码，判题结果：" + verdict.getText());
            try {
                SubmissionFacts facts = submissionFacts(submission, question);
                String transcript = MemoryTranscript.render(
                        chatMemoryStore.getMessages(new ConversationMemoryId(conversation.getId())), TRANSCRIPT_CHARS);
                String prompt = companionPrompts.reactionMessage(state, facts, submission.getCode(),
                        submission.getLanguage(), recentLines, transcript);
                answer = assistantFactory.reactionAssistant(aiSettingService.requireCredential(userId), trace)
                        .react(companionPrompts.systemMessage(question), prompt)
                        .content();
            } catch (RuntimeException e) {
                failure = e;
                log.warn("陪练反应调用失败，改用兜底话术 submissionId={}, error={}", submission.getId(), e.toString());
            }
        }
        boolean fallback = answer == null;
        if (fallback) {
            answer = FallbackReactions.pick(verdict, state.track(), recentLines, ThreadLocalRandom.current());
        }
        HintTrackEnum track = answer.getTrack() == null ? state.track() : answer.getTrack();

        CompanionMessage message = new CompanionMessage();
        message.setUserId(userId);
        message.setQuestionId(questionId);
        message.setConversationId(conversation.getId());
        message.setRole(CompanionRoleEnum.ASSISTANT.getValue());
        message.setType(CompanionMessageTypeEnum.REACTION.getValue());
        message.setHintTrack(track.getValue());
        message.setMood(answer.getMood() == null ? CompanionMoodEnum.THOUGHTFUL.getValue() : answer.getMood().getValue());
        message.setMoodIntensity(answer.getIntensity() == null ? 1 : answer.getIntensity());
        message.setSubmissionId(submission.getId());
        message.setContent(answer.getMessage());
        companionMessageService.saveMessage(message, null);
        if (trace != null) {
            if (fallback) {
                trace.fallback(message.getId(), failure);
            } else {
                trace.finish(message.getId());
            }
        }
        if (track != state.track()) {
            companionSessionService.updateHintTrack(session.getId(), track);
        }
        return reactionVO(message, fallback);
    }

    private SubmissionFacts submissionFacts(QuestionSubmit submission, Question question) {
        int attemptNumber = Math.toIntExact(questionSubmitService.count(new QueryWrapper<QuestionSubmit>()
                .eq("userId", submission.getUserId())
                .eq("questionId", submission.getQuestionId())
                .le("id", submission.getId())));
        List<QuestionSubmit> previous = questionSubmitService.list(new QueryWrapper<QuestionSubmit>()
                .select("id", "judgeInfo", "status", "createTime")
                .eq("userId", submission.getUserId())
                .eq("questionId", submission.getQuestionId())
                .lt("id", submission.getId())
                .orderByDesc("id")
                .last("limit " + PREVIOUS_SUBMISSION_LIMIT));
        return SubmissionFactsBuilder.build(submission, previous, attemptNumber, CompanionPrompts.parseJudgeConfig(question));
    }

    private static CompanionReactionVO reactionVO(CompanionMessage message, boolean fallback) {
        CompanionReactionVO vo = new CompanionReactionVO();
        vo.setMessageId(message.getId());
        vo.setSubmissionId(message.getSubmissionId());
        vo.setMood(message.getMood());
        vo.setMoodIntensity(message.getMoodIntensity());
        vo.setConversationId(message.getConversationId());
        vo.setMessage(message.getContent());
        vo.setFallback(fallback);
        return vo;
    }

    // endregion

    // region 聊天

    @Override
    public SseEmitter chat(User loginUser, CompanionChatRequest request) {
        if (request == null || request.getConversationId() == null || StringUtils.isBlank(request.getMessage())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息不能为空");
        }
        if (request.getMessage().length() > MAX_MESSAGE_CHARS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息太长了，最多 " + MAX_MESSAGE_CHARS + " 个字");
        }
        CodeSnapshot snapshot = requireSnapshot(request.getSnapshot());
        CodeQuote quote = normalizeQuote(request.getQuote());
        long userId = loginUser.getId();
        Target target = target(userId, request.getConversationId());
        AiCredential credential = aiSettingService.requireCredential(userId);
        AiCallGuard.Permit permit = aiCallGuard.acquire(userId);
        TraceRecorder trace = null;
        try {
            HintState state = hintState(target.session(), aiSettingService.getHintMode(userId));
            String systemPrompt = companionPrompts.systemMessage(target.question());
            boolean compacted = compactIfNeeded(target, credential, systemPrompt);

            CodeEditSession editSession = new CodeEditSession(snapshot.getCode(), snapshot.getLanguage(), state.editPermission());
            List<Object> tools = new ArrayList<>();
            tools.add(new SubmissionHistoryTools(userId, target.questionId(), questionSubmitService,
                    aiProperties.getCompanion().getRecentSubmissionCount(),
                    aiProperties.getCompanion().getMaxToolResultChars()));
            tools.add(new CodeEditTools(editSession));
            if (state.editPermission() == EditPermission.UNRESTRICTED) {
                tools.add(new CodeRewriteTools(editSession));
            }
            NoteEditSession noteSession = new NoteEditSession(loginUser, target.questionId(),
                    target.question().getTitle(), state.effectiveLevel(), snapshot.getCode(), noteService);
            tools.add(new NoteTools(loginUser, target.questionId(), noteService, noteSession,
                    aiProperties.getCompanion().getMaxToolResultChars()));
            String userText = request.getMessage().strip();
            String userPrompt = companionPrompts.chatMessage(state, snapshot, recentEvents(target), quote, userText);
            companionMessageService.saveUserMessage(target.conversation(), CompanionMessageTypeEnum.CHAT, userText, quote);
            companionConversationService.titleIfAbsent(target.conversation().getId(), userText);

            trace = startTrace(target, CompanionMessageTypeEnum.CHAT, userText);
            TokenStream stream = assistantFactory
                    .chatAssistant(credential, assistantFactory.conversationMemory(target.memoryId()), tools, trace)
                    .chat(systemPrompt, userPrompt);
            return streamAnswer(stream, permit, target, state, editSession, noteSession, trace,
                    CompanionMessageTypeEnum.CHAT, compacted, Function.identity());
        } catch (RuntimeException e) {
            permit.close();
            if (trace != null) {
                trace.fail(e);
            }
            throw aiFailure(e);
        }
    }

    /**
     * 引用的代码为空时当作没有引用；行号缺失或代码过长时拒绝
     */
    private static CodeQuote normalizeQuote(CodeQuote quote) {
        if (quote == null || StringUtils.isBlank(quote.getCode())) {
            return null;
        }
        if (quote.getCode().length() > MAX_QUOTE_CHARS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "选中的代码太长了，最多 " + MAX_QUOTE_CHARS + " 个字符");
        }
        if (quote.getStartLine() == null || quote.getEndLine() == null
                || quote.getStartLine() < 1 || quote.getEndLine() < quote.getStartLine()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "引用代码的行号不正确");
        }
        return quote;
    }

    // endregion

    // region 提示

    @Override
    public CompanionHintVO hint(User loginUser, CompanionHintRequest request) {
        if (request == null || request.getConversationId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        CodeSnapshot snapshot = requireSnapshot(request.getSnapshot());
        long userId = loginUser.getId();
        Target target = target(userId, request.getConversationId());
        AiCredential credential = aiSettingService.requireCredential(userId);
        TraceRecorder trace = null;
        try (AiCallGuard.Permit ignored = aiCallGuard.acquire(userId)) {
            CompanionSession session = target.session();
            HintState state = hintState(session, aiSettingService.getHintMode(userId));
            String systemPrompt = companionPrompts.systemMessage(target.question());
            compactIfNeeded(target, credential, systemPrompt);

            String recentEvents = recentEvents(target);
            String transcript = MemoryTranscript.render(chatMemoryStore.getMessages(target.memoryId()), TRANSCRIPT_CHARS);
            trace = startTrace(target, CompanionMessageTypeEnum.HINT,
                    "要一条提示（" + HintLadder.step(state.effectiveLevel()).title() + "）");
            HintAnswer answer = assistantFactory.hintAssistant(credential, trace)
                    .hint(systemPrompt, companionPrompts.hintMessage(state, snapshot, recentEvents, transcript))
                    .content();

            HintTrackEnum track = answer.getTrack() == null ? state.track() : answer.getTrack();
            HintState answered = new HintState(track, state.unlockedLevel(), state.mode());
            CodeEditProposal proposal = applyHintEdits(answer.getEdits(), snapshot, answered.editPermission());
            if (track != state.track()) {
                companionSessionService.updateHintTrack(session.getId(), track);
            }

            // 提示不挂记忆，这里把这一轮补进对话记忆，之后聊天时陪练知道自己给过什么提示
            int level = answered.effectiveLevel();
            ChatMemory memory = assistantFactory.conversationMemory(target.memoryId());
            memory.add(UserMessage.from(String.format(HINT_MEMORY_NOTE, level)
                    + (recentEvents.isEmpty() ? "" : "\n\n" + recentEvents)));
            memory.add(AiMessage.from(answer.getMessage()));

            CompanionMessage message = assistantMessage(target, CompanionMessageTypeEnum.HINT, track);
            message.setHintLevel(level);
            message.setContent(answer.getMessage());
            companionMessageService.saveMessage(message, proposal);
            trace.finish(message.getId());

            CompanionHintVO vo = new CompanionHintVO();
            vo.setMessageId(message.getId());
            vo.setHintLevel(level);
            vo.setStepTitle(HintLadder.step(level).title());
            vo.setMessage(answer.getMessage());
            vo.setCodeEdits(proposal);
            vo.setContextUsage(contextUsage(userId, target.conversation().getContextTokens(), false));
            return vo;
        } catch (RuntimeException e) {
            if (trace != null) {
                trace.fail(e);
            }
            throw aiFailure(e);
        }
    }

    /**
     * 提示里附带的修改同样走修改会话：唯一匹配和权限校验不通过的直接丢弃
     */
    private CodeEditProposal applyHintEdits(List<HintEditAnswer> edits, CodeSnapshot snapshot, EditPermission permission) {
        if (edits == null || edits.isEmpty()) {
            return null;
        }
        CodeEditSession editSession = new CodeEditSession(snapshot.getCode(), snapshot.getLanguage(), permission);
        for (HintEditAnswer edit : edits) {
            String result = editSession.replace(edit.getOldText(), edit.getNewText(), edit.getReason());
            if (!result.startsWith("已记录")) {
                log.info("提示附带的修改未采用: {}", result);
            }
        }
        return editSession.proposal().orElse(null);
    }

    // endregion

    // region 看答案

    @Override
    public SseEmitter solution(User loginUser, CompanionSolutionRequest request) {
        if (request == null || request.getConversationId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        CodeSnapshot snapshot = requireSnapshot(request.getSnapshot());
        long userId = loginUser.getId();
        Target target = target(userId, request.getConversationId());
        AiCredential credential = aiSettingService.requireCredential(userId);
        AiCallGuard.Permit permit = aiCallGuard.acquire(userId);
        TraceRecorder trace = null;
        try {
            CompanionSession session = target.session();
            HintState state = hintState(session, aiSettingService.getHintMode(userId)).withLevel(HintLadder.SOLUTION_LEVEL);
            companionSessionService.updateHintState(session.getId(), HintLadder.SOLUTION_LEVEL, state.track());
            String systemPrompt = companionPrompts.systemMessage(target.question());
            boolean compacted = compactIfNeeded(target, credential, systemPrompt);
            companionMessageService.saveUserMessage(target.conversation(), CompanionMessageTypeEnum.SOLUTION,
                    SOLUTION_USER_TEXT, null);

            CodeEditSession editSession = new CodeEditSession(snapshot.getCode(), snapshot.getLanguage(),
                    EditPermission.UNRESTRICTED);
            trace = startTrace(target, CompanionMessageTypeEnum.SOLUTION, SOLUTION_USER_TEXT);
            TokenStream stream = assistantFactory
                    .solutionAssistant(credential, assistantFactory.conversationMemory(target.memoryId()), trace)
                    .solve(systemPrompt, companionPrompts.solutionMessage(state, snapshot));
            // 参考代码不走工具：从回答里取最后一个代码块，作为整体替换建议
            Function<String, String> extractCode = answer -> {
                Matcher matcher = CODE_BLOCK.matcher(answer);
                String code = null;
                while (matcher.find()) {
                    code = matcher.group(1);
                }
                if (code != null) {
                    editSession.rewrite(code, "参考代码");
                }
                return answer;
            };
            return streamAnswer(stream, permit, target, state, editSession, null, trace,
                    CompanionMessageTypeEnum.SOLUTION, compacted, extractCode);
        } catch (RuntimeException e) {
            permit.close();
            if (trace != null) {
                trace.fail(e);
            }
            throw aiFailure(e);
        }
    }

    // endregion

    // region 档位与记忆整理

    @Override
    public CompanionHintStateVO setLevel(User loginUser, CompanionLevelRequest request) {
        if (request == null || request.getQuestionId() == null || request.getLevel() == null
                || request.getLevel() < HintLadder.MIN_LEVEL || request.getLevel() > HintLadder.MAX_HINT_LEVEL) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "档位只能设为 0 到 " + HintLadder.MAX_HINT_LEVEL + "，看答案请用参考解法");
        }
        long userId = loginUser.getId();
        requireQuestion(request.getQuestionId());
        CompanionSession session = companionSessionService.getOrCreate(userId, request.getQuestionId());
        HintTrackEnum track = HintTrackEnum.getEnumByValue(session.getHintTrack());
        companionSessionService.updateHintState(session.getId(), request.getLevel(), track);
        HintState state = new HintState(track, request.getLevel(), aiSettingService.getHintMode(userId));
        CompanionHintStateVO vo = new CompanionHintStateVO();
        vo.setQuestionId(request.getQuestionId());
        vo.setHintLevel(state.unlockedLevel());
        vo.setEffectiveLevel(state.effectiveLevel());
        return vo;
    }

    @Override
    public CompanionCompactionVO compact(User loginUser, CompanionCompactRequest request) {
        if (request == null || request.getConversationId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long userId = loginUser.getId();
        Target target = target(userId, request.getConversationId());
        AiCredential credential = aiSettingService.requireCredential(userId);
        try (AiCallGuard.Permit ignored = aiCallGuard.acquire(userId)) {
            Optional<CompanionMessage> note = runCompaction(target, credential,
                    companionPrompts.systemMessage(target.question()), "手动整理记忆");
            CompanionCompactionVO vo = new CompanionCompactionVO();
            vo.setCompacted(note.isPresent());
            note.ifPresent(message -> {
                vo.setMessageId(message.getId());
                vo.setNote(message.getContent());
            });
            vo.setContextUsage(contextUsage(userId, target.conversation().getContextTokens(), note.isPresent()));
            return vo;
        } catch (RuntimeException e) {
            throw aiFailure(e);
        }
    }

    /**
     * 上下文占用达到窗口的 compact-ratio 时，在本轮调用前先压缩记忆
     */
    private boolean compactIfNeeded(Target target, AiCredential credential, String systemPrompt) {
        int window = aiSettingService.getContextWindow(target.userId());
        Integer tokens = target.conversation().getContextTokens();
        int used = tokens == null ? 0 : tokens;
        if (used < window * aiProperties.getCompanion().getCompactRatio()) {
            return false;
        }
        return runCompaction(target, credential, systemPrompt, "上下文快满了，自动整理记忆").isPresent();
    }

    /**
     * @param reason 写进轨迹：手动还是自动整理
     */
    private Optional<CompanionMessage> runCompaction(Target target, AiCredential credential, String systemPrompt,
                                                     String reason) {
        CompanionConversation conversation = target.conversation();
        List<String> questions = companionMessageService.userQuestions(conversation.getId(), USER_QUESTION_LIMIT);
        TraceRecorder trace = startTrace(target, CompanionMessageTypeEnum.COMPACTION, reason);
        try {
            Optional<MemoryCompactor.CompactionResult> result = memoryCompactor.compact(target.memoryId(),
                    assistantFactory.compactionAssistant(credential, trace), systemPrompt,
                    aiProperties.getCompanion().getKeepRecentRounds(), questions);
            if (result.isEmpty()) {
                // 对话太短，没什么可整理的：轨迹里不留这一轮
                trace.discard();
                return Optional.empty();
            }
            companionConversationService.updateContextTokens(conversation.getId(), result.get().estimatedTokens());
            conversation.setContextTokens(result.get().estimatedTokens());
            CompanionMessage message = assistantMessage(target, CompanionMessageTypeEnum.COMPACTION, null);
            message.setContent(result.get().note());
            companionMessageService.saveMessage(message, null);
            trace.finish(message.getId());
            return Optional.of(message);
        } catch (RuntimeException e) {
            trace.fail(e);
            throw e;
        }
    }

    // endregion

    // region 流式回答

    /**
     * 把 TokenStream 接到 SSE：正文和思考逐段推送，结束时落库、推送修改建议、笔记提议和 done，并更新上下文占用。
     *
     * @param noteSession  这一轮的笔记提议，没有笔记工具时为空
     * @param trace        这一轮的轨迹记录器：工具执行完交给它，结束时标记完成或出错
     * @param finishAnswer 结束时对完整回答的处理（参考解法用它提取代码），返回要保存的正文
     */
    private SseEmitter streamAnswer(TokenStream stream, AiCallGuard.Permit permit, Target target,
                                    HintState state, CodeEditSession editSession, NoteEditSession noteSession,
                                    TraceRecorder trace, CompanionMessageTypeEnum type, boolean compacted,
                                    Function<String, String> finishAnswer) {
        CompanionSse sse = new CompanionSse(STREAM_TIMEOUT_MILLIS);
        sse.onFinish(permit::close);
        StringBuilder answer = new StringBuilder();
        AtomicInteger intermediateInput = new AtomicInteger();
        AtomicInteger intermediateOutput = new AtomicInteger();
        stream.onPartialResponse(text -> {
                    answer.append(text);
                    sse.send(CompanionSse.EVENT_DELTA, Map.of("text", text));
                })
                .onPartialThinking(thinking -> sse.send(CompanionSse.EVENT_THINKING, Map.of("text", thinking.text())))
                .onToolExecuted(trace::recordTool)
                .onIntermediateResponse(response -> {
                    TokenUsage usage = response.tokenUsage();
                    if (usage != null) {
                        intermediateInput.addAndGet(nullToZero(usage.inputTokenCount()));
                        intermediateOutput.addAndGet(nullToZero(usage.outputTokenCount()));
                    }
                })
                .onCompleteResponse(response -> {
                    try {
                        String content = finishAnswer.apply(answer.isEmpty() ? textOf(response) : answer.toString());
                        CodeEditProposal proposal = editSession.proposal().orElse(null);
                        List<NoteProposal> notes = noteSession == null ? List.of() : noteSession.proposals();
                        CompanionMessage message = assistantMessage(target, type, state.track());
                        message.setHintLevel(type == CompanionMessageTypeEnum.SOLUTION ? HintLadder.SOLUTION_LEVEL : null);
                        message.setContent(StringUtils.defaultIfBlank(content, "（没有回答内容）"));
                        companionMessageService.saveMessage(message, proposal, notes);
                        trace.finish(message.getId());

                        int contextTokens = lastCallContextTokens(response, intermediateInput.get(), intermediateOutput.get());
                        companionConversationService.updateContextTokens(target.conversation().getId(), contextTokens);
                        if (proposal != null) {
                            sse.send(CompanionSse.EVENT_EDITS, proposal);
                        }
                        if (!notes.isEmpty()) {
                            sse.send(CompanionSse.EVENT_NOTES, notes);
                        }
                        CompanionStreamDoneVO done = new CompanionStreamDoneVO();
                        done.setMessageId(message.getId());
                        done.setHintLevel(state.unlockedLevel());
                        done.setContextUsage(contextUsage(target.userId(), contextTokens, false));
                        done.setCompacted(compacted);
                        sse.send(CompanionSse.EVENT_DONE, done);
                    } catch (RuntimeException e) {
                        log.error("陪练回答收尾失败 conversationId={}", target.conversation().getId(), e);
                        trace.fail(e);
                        sendError(sse, AiErrorTranslator.translate(e));
                    } finally {
                        sse.complete();
                        permit.close();
                    }
                })
                .onError(error -> {
                    BusinessException translated = AiErrorTranslator.translate(error);
                    log.warn("陪练流式回答失败 conversationId={}, code={}, error={}", target.conversation().getId(),
                            translated.getCode(), error.toString());
                    trace.fail(error);
                    sendError(sse, translated);
                    sse.complete();
                    permit.close();
                })
                .start();
        return sse.emitter();
    }

    /**
     * 当前上下文 ≈ 最后一次调用的输入加输出；TokenStream 结束时给的是整轮（含工具往返）的累计值，要减去中间各次
     */
    static int lastCallContextTokens(ChatResponse response, int intermediateInput, int intermediateOutput) {
        TokenUsage total = response.tokenUsage();
        if (total == null) {
            return 0;
        }
        int lastInput = nullToZero(total.inputTokenCount()) - intermediateInput;
        int lastOutput = nullToZero(total.outputTokenCount()) - intermediateOutput;
        return Math.max(0, lastInput) + Math.max(0, lastOutput);
    }

    private static void sendError(CompanionSse sse, BusinessException error) {
        sse.send(CompanionSse.EVENT_ERROR, Map.of("code", error.getCode(), "message", error.getMessage()));
    }

    private static String textOf(ChatResponse response) {
        return response.aiMessage() == null ? "" : StringUtils.defaultString(response.aiMessage().text());
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    // endregion

    // region 公共

    private Question requireQuestion(long questionId) {
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        return question;
    }

    private static CodeSnapshot requireSnapshot(CodeSnapshot snapshot) {
        CodeSnapshot result = snapshot == null ? new CodeSnapshot() : snapshot;
        if (result.getCode() != null && result.getCode().length() > MAX_REQUEST_CODE_CHARS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码太长了，最多 " + MAX_REQUEST_CODE_CHARS + " 个字符");
        }
        if (result.getCode() == null) {
            result.setCode("");
        }
        return result;
    }

    private static HintState hintState(CompanionSession session, HintModeEnum mode) {
        int level = session.getHintLevel() == null ? 0 : session.getHintLevel();
        return new HintState(HintTrackEnum.getEnumByValue(session.getHintTrack()), level, mode);
    }

    private ContextUsageVO contextUsage(long userId, Integer usedTokens, boolean estimated) {
        int used = usedTokens == null ? 0 : usedTokens;
        int limit = aiSettingService.getContextWindow(userId);
        return new ContextUsageVO(used, limit, limit == 0 ? 0 : (double) used / limit,
                aiProperties.getCompanion().getCompactRatio(), estimated);
    }

    /**
     * 一次调用作用的对象：对话、它所属题目的会话和题目
     */
    private record Target(CompanionConversation conversation, CompanionSession session, Question question) {

        long userId() {
            return session.getUserId();
        }

        long questionId() {
            return session.getQuestionId();
        }

        ConversationMemoryId memoryId() {
            return new ConversationMemoryId(conversation.getId());
        }
    }

    /**
     * 取用户自己的对话，并把它设为当前对话（之后的提交反应记在这里）
     */
    private Target target(long userId, long conversationId) {
        CompanionConversation conversation = companionConversationService.requireOwned(userId, conversationId);
        Question question = requireQuestion(conversation.getQuestionId());
        CompanionSession session = companionSessionService.getOrCreate(userId, conversation.getQuestionId());
        companionConversationService.activate(session, conversation);
        return new Target(conversation, session, question);
    }

    private TraceRecorder startTrace(Target target, CompanionMessageTypeEnum type, String userText) {
        return companionTraceService.startTurn(target.userId(), target.questionId(), target.conversation().getId(),
                type.getValue(), userText);
    }

    private static CompanionMessage assistantMessage(Target target, CompanionMessageTypeEnum type, HintTrackEnum track) {
        CompanionMessage message = new CompanionMessage();
        message.setUserId(target.userId());
        message.setQuestionId(target.questionId());
        message.setConversationId(target.conversation().getId());
        message.setRole(CompanionRoleEnum.ASSISTANT.getValue());
        message.setType(type.getValue());
        message.setHintTrack(track == null ? null : track.getValue());
        return message;
    }

    /**
     * 上次写入记忆之后用户的提交和陪练当时的反应；会写进本轮用户消息，从而进入记忆
     */
    private String recentEvents(Target target) {
        CompanionConversation conversation = target.conversation();
        List<CompanionMessage> reactions = companionMessageService.reactionsSince(conversation.getId(),
                conversation.getMemoryUpdateTime(), RECENT_EVENT_LIMIT);
        if (reactions.isEmpty()) {
            return "";
        }
        Set<Long> submissionIds = reactions.stream()
                .map(CompanionMessage::getSubmissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, QuestionSubmit> submissions = submissionIds.isEmpty() ? Map.of()
                : questionSubmitService.listByIds(submissionIds).stream()
                .collect(Collectors.toMap(QuestionSubmit::getId, Function.identity()));
        StringBuilder text = new StringBuilder("【").append(MemoryTranscript.RECENT_EVENTS).append("】\n");
        for (CompanionMessage reaction : reactions) {
            QuestionSubmit submission = submissions.get(reaction.getSubmissionId());
            String verdict = submission == null ? "未知" : VerdictClassifier.classify(submission).getText();
            String time = reaction.getCreateTime() == null ? "" : EVENT_TIME_FORMAT.format(reaction.getCreateTime().toInstant()) + " ";
            text.append("- ").append(time).append("提交结果：").append(verdict)
                    .append("；你当时说：").append(reaction.getContent()).append("\n");
        }
        return text.toString().strip();
    }

    /**
     * 只把模型调用的异常翻译成给用户看的 AI 错误，其余异常（如数据库）照常抛出，由全局异常处理记为系统错误
     */
    private static RuntimeException aiFailure(RuntimeException error) {
        if (error instanceof LangChain4jException) {
            return AiErrorTranslator.translate(error);
        }
        return error;
    }

    // endregion
}
