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
import com.hjl.oj.companion.memory.MemoryCompactor;
import com.hjl.oj.companion.memory.MemoryTranscript;
import com.hjl.oj.companion.memory.MysqlChatMemoryStore;
import com.hjl.oj.companion.memory.SessionMemoryId;
import com.hjl.oj.companion.prompt.CompanionPrompts;
import com.hjl.oj.companion.reaction.FallbackReactions;
import com.hjl.oj.companion.stream.CompanionSse;
import com.hjl.oj.companion.tool.CodeEditTools;
import com.hjl.oj.companion.tool.CodeRewriteTools;
import com.hjl.oj.companion.tool.SubmissionHistoryTools;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.companion.CodeSnapshot;
import com.hjl.oj.model.dto.companion.CompanionChatRequest;
import com.hjl.oj.model.dto.companion.CompanionCompactRequest;
import com.hjl.oj.model.dto.companion.CompanionHintRequest;
import com.hjl.oj.model.dto.companion.CompanionLevelRequest;
import com.hjl.oj.model.dto.companion.CompanionReactRequest;
import com.hjl.oj.model.dto.companion.CompanionSolutionRequest;
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
import com.hjl.oj.model.vo.CompanionHintStateVO;
import com.hjl.oj.model.vo.CompanionHintVO;
import com.hjl.oj.model.vo.CompanionMessageVO;
import com.hjl.oj.model.vo.CompanionReactionVO;
import com.hjl.oj.model.vo.CompanionSessionVO;
import com.hjl.oj.model.vo.CompanionStreamDoneVO;
import com.hjl.oj.model.vo.ContextUsageVO;
import com.hjl.oj.model.vo.HintStepVO;
import com.hjl.oj.service.AiSettingService;
import com.hjl.oj.service.CompanionMessageService;
import com.hjl.oj.service.CompanionService;
import com.hjl.oj.service.CompanionSessionService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final Pattern CODE_BLOCK = Pattern.compile("```[\\w+#-]*\\n(.*?)```", Pattern.DOTALL);

    private static final DateTimeFormatter EVENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private static final String HINT_MEMORY_NOTE = "【场景】用户点了「再给点提示」，要第 %d 级的提示。";

    private static final String SOLUTION_USER_TEXT = "我想看答案";

    private final QuestionService questionService;

    private final QuestionSubmitService questionSubmitService;

    private final AiSettingService aiSettingService;

    private final CompanionSessionService companionSessionService;

    private final CompanionMessageService companionMessageService;

    private final CompanionAssistantFactory assistantFactory;

    private final CompanionPrompts companionPrompts;

    private final MemoryCompactor memoryCompactor;

    private final MysqlChatMemoryStore chatMemoryStore;

    private final AiCallGuard aiCallGuard;

    private final AiProperties aiProperties;

    public CompanionServiceImpl(QuestionService questionService, QuestionSubmitService questionSubmitService,
                                AiSettingService aiSettingService, CompanionSessionService companionSessionService,
                                CompanionMessageService companionMessageService,
                                CompanionAssistantFactory assistantFactory, CompanionPrompts companionPrompts,
                                MemoryCompactor memoryCompactor, MysqlChatMemoryStore chatMemoryStore,
                                AiCallGuard aiCallGuard, AiProperties aiProperties) {
        this.questionService = questionService;
        this.questionSubmitService = questionSubmitService;
        this.aiSettingService = aiSettingService;
        this.companionSessionService = companionSessionService;
        this.companionMessageService = companionMessageService;
        this.assistantFactory = assistantFactory;
        this.companionPrompts = companionPrompts;
        this.memoryCompactor = memoryCompactor;
        this.chatMemoryStore = chatMemoryStore;
        this.aiCallGuard = aiCallGuard;
        this.aiProperties = aiProperties;
    }

    // region 会话与历史

    @Override
    public CompanionSessionVO getSession(User loginUser, long questionId) {
        long userId = loginUser.getId();
        requireQuestion(questionId);
        CompanionSession session = companionSessionService.getOrCreate(userId, questionId);
        HintState state = hintState(session, aiSettingService.getHintMode(userId));
        int pageSize = aiProperties.getCompanion().getHistoryPageSize();
        List<CompanionMessage> messages = companionMessageService.listLatest(userId, questionId, null, pageSize + 1);
        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = messages.subList(1, messages.size());
        }

        CompanionSessionVO vo = new CompanionSessionVO();
        vo.setQuestionId(questionId);
        vo.setHintLevel(state.unlockedLevel());
        vo.setEffectiveLevel(state.effectiveLevel());
        vo.setHintTrack(state.track().getValue());
        vo.setHintMode(state.mode().getValue());
        Map<String, List<HintStepVO>> steps = new LinkedHashMap<>();
        for (HintTrackEnum track : HintTrackEnum.values()) {
            steps.put(track.getValue(), HintLadder.steps(track).stream()
                    .map(step -> new HintStepVO(step.level(), step.title(), step.description()))
                    .toList());
        }
        vo.setHintSteps(steps);
        vo.setCanCallModel(aiSettingService.canCallModel(userId));
        vo.setMockMode(aiProperties.getMode() == AiMode.MOCK);
        vo.setPersonaName(aiProperties.getCompanion().getPersonaName());
        vo.setContextUsage(contextUsage(userId, session.getContextTokens(), false));
        vo.setMessages(messages.stream().map(companionMessageService::toVO).toList());
        vo.setHasMoreMessages(hasMore);
        return vo;
    }

    @Override
    public List<CompanionMessageVO> listMessages(User loginUser, long questionId, Long beforeId) {
        int pageSize = aiProperties.getCompanion().getHistoryPageSize();
        return companionMessageService.listLatest(loginUser.getId(), questionId, beforeId, pageSize).stream()
                .map(companionMessageService::toVO)
                .toList();
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
        HintState state = hintState(session, aiSettingService.getHintMode(userId));
        List<String> recentLines = companionMessageService.recentReactionLines(userId, questionId, RECENT_REACTION_LINES);

        ReactionAnswer answer = null;
        if (aiSettingService.canCallModel(userId)) {
            try {
                SubmissionFacts facts = submissionFacts(submission, question);
                String transcript = MemoryTranscript.render(
                        chatMemoryStore.getMessages(new SessionMemoryId(userId, questionId)), TRANSCRIPT_CHARS);
                String prompt = companionPrompts.reactionMessage(state, facts, submission.getCode(),
                        submission.getLanguage(), recentLines, transcript);
                answer = assistantFactory.reactionAssistant(aiSettingService.requireCredential(userId))
                        .react(companionPrompts.systemMessage(question), prompt)
                        .content();
            } catch (RuntimeException e) {
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
        message.setRole(CompanionRoleEnum.ASSISTANT.getValue());
        message.setType(CompanionMessageTypeEnum.REACTION.getValue());
        message.setHintTrack(track.getValue());
        message.setMood(answer.getMood() == null ? CompanionMoodEnum.THOUGHTFUL.getValue() : answer.getMood().getValue());
        message.setMoodIntensity(answer.getIntensity() == null ? 1 : answer.getIntensity());
        message.setSubmissionId(submission.getId());
        message.setContent(answer.getMessage());
        companionMessageService.saveMessage(message, null);
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
        vo.setHintTrack(message.getHintTrack());
        vo.setMessage(message.getContent());
        vo.setFallback(fallback);
        return vo;
    }

    // endregion

    // region 聊天

    @Override
    public SseEmitter chat(User loginUser, CompanionChatRequest request) {
        if (request == null || request.getQuestionId() == null || StringUtils.isBlank(request.getMessage())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息不能为空");
        }
        if (request.getMessage().length() > MAX_MESSAGE_CHARS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息太长了，最多 " + MAX_MESSAGE_CHARS + " 个字");
        }
        CodeSnapshot snapshot = requireSnapshot(request.getSnapshot());
        long userId = loginUser.getId();
        long questionId = request.getQuestionId();
        Question question = requireQuestion(questionId);
        AiCredential credential = aiSettingService.requireCredential(userId);
        AiCallGuard.Permit permit = aiCallGuard.acquire(userId);
        try {
            CompanionSession session = companionSessionService.getOrCreate(userId, questionId);
            HintState state = hintState(session, aiSettingService.getHintMode(userId));
            String systemPrompt = companionPrompts.systemMessage(question);
            SessionMemoryId memoryId = new SessionMemoryId(userId, questionId);
            boolean compacted = compactIfNeeded(session, credential, systemPrompt, memoryId);

            CodeEditSession editSession = new CodeEditSession(snapshot.getCode(), snapshot.getLanguage(), state.editPermission());
            List<Object> tools = new ArrayList<>();
            tools.add(new SubmissionHistoryTools(userId, questionId, questionSubmitService,
                    aiProperties.getCompanion().getRecentSubmissionCount(),
                    aiProperties.getCompanion().getMaxToolResultChars()));
            tools.add(new CodeEditTools(editSession));
            if (state.editPermission() == EditPermission.UNRESTRICTED) {
                tools.add(new CodeRewriteTools(editSession));
            }
            String userPrompt = companionPrompts.chatMessage(state,
                    snapshot, recentEvents(userId, questionId, session), request.getMessage().strip());
            saveUserMessage(userId, questionId, CompanionMessageTypeEnum.CHAT, request.getMessage().strip());

            TokenStream stream = assistantFactory
                    .chatAssistant(credential, assistantFactory.sessionMemory(memoryId), tools)
                    .chat(systemPrompt, userPrompt);
            return streamAnswer(stream, permit, session, state, editSession, CompanionMessageTypeEnum.CHAT,
                    compacted, Function.identity());
        } catch (RuntimeException e) {
            permit.close();
            throw aiFailure(e);
        }
    }

    // endregion

    // region 分级提示

    @Override
    public CompanionHintVO hint(User loginUser, CompanionHintRequest request) {
        if (request == null || request.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        CodeSnapshot snapshot = requireSnapshot(request.getSnapshot());
        long userId = loginUser.getId();
        long questionId = request.getQuestionId();
        Question question = requireQuestion(questionId);
        AiCredential credential = aiSettingService.requireCredential(userId);
        try (AiCallGuard.Permit ignored = aiCallGuard.acquire(userId)) {
            CompanionSession session = companionSessionService.getOrCreate(userId, questionId);
            HintState state = hintState(session, aiSettingService.getHintMode(userId));
            int targetLevel = request.getTargetLevel() == null
                    ? Math.min(state.unlockedLevel() + 1, HintLadder.MAX_HINT_LEVEL)
                    : request.getTargetLevel();
            if (targetLevel < 1 || targetLevel > HintLadder.MAX_HINT_LEVEL) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示等级只能是 1 到 " + HintLadder.MAX_HINT_LEVEL + "，看答案请用参考解法");
            }
            String systemPrompt = companionPrompts.systemMessage(question);
            SessionMemoryId memoryId = new SessionMemoryId(userId, questionId);
            compactIfNeeded(session, credential, systemPrompt, memoryId);

            String recentEvents = recentEvents(userId, questionId, session);
            String transcript = MemoryTranscript.render(chatMemoryStore.getMessages(memoryId), TRANSCRIPT_CHARS);
            HintAnswer answer = assistantFactory.hintAssistant(credential)
                    .hint(systemPrompt, companionPrompts.hintMessage(state, targetLevel, snapshot, recentEvents, transcript))
                    .content();

            int unlockedLevel = Math.max(state.unlockedLevel(), targetLevel);
            HintState unlocked = new HintState(
                    answer.getTrack() == null ? state.track() : answer.getTrack(), unlockedLevel, state.mode());
            CodeEditProposal proposal = applyHintEdits(answer.getEdits(), snapshot, unlocked.editPermission());
            companionSessionService.updateHintState(session.getId(), unlockedLevel, unlocked.track());

            // 提示不挂记忆，这里把这一轮补进会话记忆，之后聊天时陪练知道自己给过什么提示
            ChatMemory memory = assistantFactory.sessionMemory(memoryId);
            memory.add(UserMessage.from(String.format(HINT_MEMORY_NOTE, targetLevel)
                    + (recentEvents.isEmpty() ? "" : "\n\n" + recentEvents)));
            memory.add(AiMessage.from(answer.getMessage()));

            CompanionMessage message = new CompanionMessage();
            message.setUserId(userId);
            message.setQuestionId(questionId);
            message.setRole(CompanionRoleEnum.ASSISTANT.getValue());
            message.setType(CompanionMessageTypeEnum.HINT.getValue());
            message.setHintLevel(targetLevel);
            message.setHintTrack(unlocked.track().getValue());
            message.setContent(answer.getMessage());
            companionMessageService.saveMessage(message, proposal);

            CompanionHintVO vo = new CompanionHintVO();
            vo.setMessageId(message.getId());
            vo.setHintLevel(targetLevel);
            vo.setHintTrack(unlocked.track().getValue());
            vo.setStepTitle(HintLadder.step(unlocked.track(), targetLevel).title());
            vo.setMessage(answer.getMessage());
            vo.setCodeEdits(proposal);
            vo.setContextUsage(contextUsage(userId, session.getContextTokens(), false));
            return vo;
        } catch (RuntimeException e) {
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
        if (request == null || request.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        CodeSnapshot snapshot = requireSnapshot(request.getSnapshot());
        long userId = loginUser.getId();
        long questionId = request.getQuestionId();
        Question question = requireQuestion(questionId);
        AiCredential credential = aiSettingService.requireCredential(userId);
        AiCallGuard.Permit permit = aiCallGuard.acquire(userId);
        try {
            CompanionSession session = companionSessionService.getOrCreate(userId, questionId);
            HintState state = hintState(session, aiSettingService.getHintMode(userId)).withLevel(HintLadder.SOLUTION_LEVEL);
            companionSessionService.updateHintState(session.getId(), HintLadder.SOLUTION_LEVEL, state.track());
            String systemPrompt = companionPrompts.systemMessage(question);
            SessionMemoryId memoryId = new SessionMemoryId(userId, questionId);
            boolean compacted = compactIfNeeded(session, credential, systemPrompt, memoryId);
            saveUserMessage(userId, questionId, CompanionMessageTypeEnum.SOLUTION, SOLUTION_USER_TEXT);

            CodeEditSession editSession = new CodeEditSession(snapshot.getCode(), snapshot.getLanguage(),
                    EditPermission.UNRESTRICTED);
            TokenStream stream = assistantFactory
                    .solutionAssistant(credential, assistantFactory.sessionMemory(memoryId))
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
            return streamAnswer(stream, permit, session, state, editSession, CompanionMessageTypeEnum.SOLUTION,
                    compacted, extractCode);
        } catch (RuntimeException e) {
            permit.close();
            throw aiFailure(e);
        }
    }

    // endregion

    // region 等级与记忆整理

    @Override
    public CompanionHintStateVO setLevel(User loginUser, CompanionLevelRequest request) {
        if (request == null || request.getQuestionId() == null || request.getLevel() == null
                || request.getLevel() < HintLadder.MIN_LEVEL || request.getLevel() > HintLadder.MAX_HINT_LEVEL) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "等级只能设为 0 到 " + HintLadder.MAX_HINT_LEVEL + "，看答案请用参考解法");
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
        vo.setHintTrack(track.getValue());
        return vo;
    }

    @Override
    public CompanionCompactionVO compact(User loginUser, CompanionCompactRequest request) {
        if (request == null || request.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long userId = loginUser.getId();
        long questionId = request.getQuestionId();
        Question question = requireQuestion(questionId);
        AiCredential credential = aiSettingService.requireCredential(userId);
        try (AiCallGuard.Permit ignored = aiCallGuard.acquire(userId)) {
            CompanionSession session = companionSessionService.getOrCreate(userId, questionId);
            Optional<CompanionMessage> note = runCompaction(session, credential, companionPrompts.systemMessage(question),
                    new SessionMemoryId(userId, questionId));
            CompanionCompactionVO vo = new CompanionCompactionVO();
            vo.setCompacted(note.isPresent());
            note.ifPresent(message -> {
                vo.setMessageId(message.getId());
                vo.setNote(message.getContent());
            });
            CompanionSession refreshed = companionSessionService.getOrCreate(userId, questionId);
            vo.setContextUsage(contextUsage(userId, refreshed.getContextTokens(), note.isPresent()));
            return vo;
        } catch (RuntimeException e) {
            throw aiFailure(e);
        }
    }

    /**
     * 上下文占用达到窗口的 compact-ratio 时，在本轮调用前先压缩记忆
     */
    private boolean compactIfNeeded(CompanionSession session, AiCredential credential, String systemPrompt,
                                    SessionMemoryId memoryId) {
        int window = aiSettingService.getContextWindow(session.getUserId());
        int used = session.getContextTokens() == null ? 0 : session.getContextTokens();
        if (used < window * aiProperties.getCompanion().getCompactRatio()) {
            return false;
        }
        return runCompaction(session, credential, systemPrompt, memoryId).isPresent();
    }

    private Optional<CompanionMessage> runCompaction(CompanionSession session, AiCredential credential,
                                                     String systemPrompt, SessionMemoryId memoryId) {
        List<String> questions = companionMessageService.userQuestions(session.getUserId(), session.getQuestionId(),
                USER_QUESTION_LIMIT);
        Optional<MemoryCompactor.CompactionResult> result = memoryCompactor.compact(memoryId,
                assistantFactory.compactionAssistant(credential), systemPrompt,
                aiProperties.getCompanion().getKeepRecentRounds(), questions);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        companionSessionService.updateContextTokens(session.getId(), result.get().estimatedTokens());
        session.setContextTokens(result.get().estimatedTokens());
        CompanionMessage message = new CompanionMessage();
        message.setUserId(session.getUserId());
        message.setQuestionId(session.getQuestionId());
        message.setRole(CompanionRoleEnum.ASSISTANT.getValue());
        message.setType(CompanionMessageTypeEnum.COMPACTION.getValue());
        message.setContent(result.get().note());
        companionMessageService.saveMessage(message, null);
        return Optional.of(message);
    }

    // endregion

    // region 流式回答

    /**
     * 把 TokenStream 接到 SSE：正文和思考逐段推送，结束时落库、推送修改建议和 done，并更新上下文占用。
     *
     * @param finishAnswer 结束时对完整回答的处理（参考解法用它提取代码），返回要保存的正文
     */
    private SseEmitter streamAnswer(TokenStream stream, AiCallGuard.Permit permit, CompanionSession session,
                                    HintState state, CodeEditSession editSession, CompanionMessageTypeEnum type,
                                    boolean compacted, Function<String, String> finishAnswer) {
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
                        CompanionMessage message = new CompanionMessage();
                        message.setUserId(session.getUserId());
                        message.setQuestionId(session.getQuestionId());
                        message.setRole(CompanionRoleEnum.ASSISTANT.getValue());
                        message.setType(type.getValue());
                        message.setHintLevel(type == CompanionMessageTypeEnum.SOLUTION ? HintLadder.SOLUTION_LEVEL : null);
                        message.setHintTrack(state.track().getValue());
                        message.setContent(StringUtils.defaultIfBlank(content, "（没有回答内容）"));
                        companionMessageService.saveMessage(message, proposal);

                        int contextTokens = lastCallContextTokens(response, intermediateInput.get(), intermediateOutput.get());
                        companionSessionService.updateContextTokens(session.getId(), contextTokens);
                        if (proposal != null) {
                            sse.send(CompanionSse.EVENT_EDITS, proposal);
                        }
                        CompanionStreamDoneVO done = new CompanionStreamDoneVO();
                        done.setMessageId(message.getId());
                        done.setHintLevel(state.unlockedLevel());
                        done.setHintTrack(state.track().getValue());
                        done.setContextUsage(contextUsage(session.getUserId(), contextTokens, false));
                        done.setCompacted(compacted);
                        sse.send(CompanionSse.EVENT_DONE, done);
                    } catch (RuntimeException e) {
                        log.error("陪练回答收尾失败 sessionId={}", session.getId(), e);
                        sendError(sse, AiErrorTranslator.translate(e));
                    } finally {
                        sse.complete();
                        permit.close();
                    }
                })
                .onError(error -> {
                    BusinessException translated = AiErrorTranslator.translate(error);
                    log.warn("陪练流式回答失败 sessionId={}, code={}, error={}", session.getId(), translated.getCode(), error.toString());
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

    private void saveUserMessage(long userId, long questionId, CompanionMessageTypeEnum type, String content) {
        CompanionMessage message = new CompanionMessage();
        message.setUserId(userId);
        message.setQuestionId(questionId);
        message.setRole(CompanionRoleEnum.USER.getValue());
        message.setType(type.getValue());
        message.setContent(content);
        companionMessageService.saveMessage(message, null);
    }

    /**
     * 上次写入记忆之后用户的提交和陪练当时的反应；会写进本轮用户消息，从而进入记忆
     */
    private String recentEvents(long userId, long questionId, CompanionSession session) {
        List<CompanionMessage> reactions = companionMessageService.reactionsSince(userId, questionId,
                session.getMemoryUpdateTime(), RECENT_EVENT_LIMIT);
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
