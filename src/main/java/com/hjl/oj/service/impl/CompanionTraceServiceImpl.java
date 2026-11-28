package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hjl.oj.companion.memory.ConversationMemoryId;
import com.hjl.oj.companion.memory.MemoryTranscript;
import com.hjl.oj.companion.memory.MysqlChatMemoryStore;
import com.hjl.oj.companion.trace.TraceJson;
import com.hjl.oj.companion.trace.TracePromptSection;
import com.hjl.oj.companion.trace.TracePrompts;
import com.hjl.oj.companion.trace.TraceRecorder;
import com.hjl.oj.companion.trace.TraceTool;
import com.hjl.oj.companion.trace.TraceToolCall;
import com.hjl.oj.mapper.CompanionTraceStepMapper;
import com.hjl.oj.mapper.CompanionTraceTurnMapper;
import com.hjl.oj.model.entity.CompanionConversation;
import com.hjl.oj.model.entity.CompanionMessage;
import com.hjl.oj.model.entity.CompanionTraceStep;
import com.hjl.oj.model.entity.CompanionTraceTurn;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.CompanionMessageTypeEnum;
import com.hjl.oj.model.vo.CompanionTraceStepVO;
import com.hjl.oj.model.vo.CompanionTraceTurnVO;
import com.hjl.oj.model.vo.CompanionTraceVO;
import com.hjl.oj.service.CompanionConversationService;
import com.hjl.oj.service.CompanionMessageService;
import com.hjl.oj.service.CompanionTraceService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 鸭鸭的轨迹。每一轮一行（companion_trace_turn），每次模型调用、工具调用一行（companion_trace_step）。
 *
 * <p>系统提示词和工具清单每轮都一样的居多，和这段对话上一轮相同时存空，读的时候沿用上一轮的。
 */
@Slf4j
@Service
public class CompanionTraceServiceImpl extends ServiceImpl<CompanionTraceTurnMapper, CompanionTraceTurn>
        implements CompanionTraceService {

    /**
     * 一段对话最多返回这么多轮（最近的）
     */
    static final int MAX_TURNS = 300;

    /**
     * 最早的消息比第一轮记录早这么多，就算「开始于记录轨迹之前」
     */
    private static final long RECORDING_GAP_MILLIS = 2000;

    private static final String HINT_NOTE = "【场景】用户点了「给点提示」";

    private static final String COMPACTION_NOTE = "【之前对话的整理笔记】";

    private final CompanionTraceStepMapper stepMapper;

    private final CompanionConversationService companionConversationService;

    private final CompanionMessageService companionMessageService;

    private final MysqlChatMemoryStore chatMemoryStore;

    public CompanionTraceServiceImpl(CompanionTraceStepMapper stepMapper,
                                     CompanionConversationService companionConversationService,
                                     CompanionMessageService companionMessageService,
                                     MysqlChatMemoryStore chatMemoryStore) {
        this.stepMapper = stepMapper;
        this.companionConversationService = companionConversationService;
        this.companionMessageService = companionMessageService;
        this.chatMemoryStore = chatMemoryStore;
    }

    // region 记录

    @Override
    public TraceRecorder startTurn(long userId, long questionId, long conversationId, String type, String userText) {
        try {
            CompanionTraceTurn turn = new CompanionTraceTurn();
            turn.setUserId(userId);
            turn.setQuestionId(questionId);
            turn.setConversationId(conversationId);
            turn.setType(type);
            turn.setStatus(CompanionTraceTurn.STATUS_RUNNING);
            turn.setUserText(StringUtils.abbreviate(userText, 4000));
            turn.setStartTime(new Date());
            this.save(turn);
            return new TraceRecorder(this, turn.getId(), conversationId);
        } catch (RuntimeException e) {
            log.warn("鸭鸭轨迹开始一轮失败，这一轮不记录：{}", e.toString());
            return TraceRecorder.disabled();
        }
    }

    @Override
    public void describeTurn(long turnId, long conversationId, String systemPrompt, String userPrompt,
                             List<TraceTool> tools) {
        String toolsJson = TraceJson.write(tools == null ? List.of() : tools);
        UpdateWrapper<CompanionTraceTurn> update = new UpdateWrapper<CompanionTraceTurn>()
                .eq("id", turnId)
                .set("userPrompt", userPrompt);
        if (systemPrompt != null && !systemPrompt.equals(previous(turnId, conversationId, "systemPrompt"))) {
            update.set("systemPrompt", systemPrompt);
        }
        if (!toolsJson.equals(previous(turnId, conversationId, "tools"))) {
            update.set("tools", toolsJson);
        }
        this.update(update);
    }

    /**
     * 这段对话里更早的一轮最后一次存下的系统提示词（或工具清单）
     */
    private String previous(long turnId, long conversationId, String column) {
        CompanionTraceTurn turn = this.getOne(new QueryWrapper<CompanionTraceTurn>()
                .select(column)
                .eq("conversationId", conversationId)
                .lt("id", turnId)
                .isNotNull(column)
                .orderByDesc("id")
                .last("limit 1"));
        if (turn == null) {
            return null;
        }
        return "systemPrompt".equals(column) ? turn.getSystemPrompt() : turn.getTools();
    }

    @Override
    public void saveStep(CompanionTraceStep step) {
        stepMapper.insert(step);
    }

    @Override
    public void finishTurn(long turnId, String status, Long messageId, String error) {
        this.update(new UpdateWrapper<CompanionTraceTurn>()
                .eq("id", turnId)
                .set("status", status)
                .set("messageId", messageId)
                .set("error", error)
                .set("endTime", new Date()));
    }

    @Override
    public void discardTurn(long turnId) {
        stepMapper.delete(new QueryWrapper<CompanionTraceStep>().eq("turnId", turnId));
        this.removeById(turnId);
    }

    @Override
    public void deleteByConversation(long conversationId) {
        stepMapper.delete(new QueryWrapper<CompanionTraceStep>().eq("conversationId", conversationId));
        this.remove(new QueryWrapper<CompanionTraceTurn>().eq("conversationId", conversationId));
    }

    // endregion

    // region 查看

    @Override
    public CompanionTraceVO getTrace(User loginUser, long conversationId) {
        CompanionConversation conversation = companionConversationService.requireOwned(loginUser.getId(),
                conversationId);
        CompanionTraceVO vo = new CompanionTraceVO();
        vo.setConversationId(conversationId);
        vo.setTitle(conversation.getTitle());

        List<CompanionTraceTurn> turns = new ArrayList<>(this.list(new QueryWrapper<CompanionTraceTurn>()
                .eq("conversationId", conversationId)
                .orderByDesc("id")
                .last("limit " + MAX_TURNS)));
        Collections.reverse(turns);
        if (turns.isEmpty()) {
            fromMemory(vo, conversationId);
            vo.setRecordedFromStart(!companionMessageService.hasMessages(conversationId));
            return vo;
        }

        Map<Long, List<CompanionTraceStep>> steps = stepMapper.selectList(new QueryWrapper<CompanionTraceStep>()
                        .eq("conversationId", conversationId)
                        .ge("turnId", turns.get(0).getId())
                        .orderByAsc("id"))
                .stream()
                .collect(Collectors.groupingBy(CompanionTraceStep::getTurnId, LinkedHashMap::new, Collectors.toList()));
        Integer systemIndex = null;
        Integer toolsIndex = null;
        for (CompanionTraceTurn turn : turns) {
            if (turn.getSystemPrompt() != null) {
                systemIndex = indexOf(vo.getSystemPrompts(), turn.getSystemPrompt());
            }
            if (turn.getTools() != null) {
                List<TraceTool> tools = TraceJson.read(turn.getTools(), TraceJson.TOOLS);
                toolsIndex = indexOf(vo.getToolSets(), tools == null ? List.<TraceTool>of() : tools);
            }
            List<CompanionTraceStep> turnSteps = steps.getOrDefault(turn.getId(), List.of());
            CompanionTraceTurnVO turnVO = turnVO(turn, turnSteps);
            // 没调用模型的一轮（比如兜底话术）没有提示词和工具
            boolean calledModel = turnSteps.stream().anyMatch(step -> CompanionTraceStep.KIND_MODEL.equals(step.getKind()));
            turnVO.setSystemPromptIndex(calledModel ? systemIndex : null);
            turnVO.setToolSetIndex(calledModel ? toolsIndex : null);
            vo.getTurns().add(turnVO);
        }
        vo.setRecordedFromStart(!hasMessagesBefore(conversationId, turns.get(0).getStartTime()));
        return vo;
    }

    /**
     * 列表里相同的那一项的下标；没有就加到末尾
     */
    private static <T> int indexOf(List<T> list, T value) {
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i), value)) {
                return i;
            }
        }
        list.add(value);
        return list.size() - 1;
    }

    private boolean hasMessagesBefore(long conversationId, Date time) {
        if (time == null) {
            return false;
        }
        CompanionMessage first = companionMessageService.getOne(new QueryWrapper<CompanionMessage>()
                .select("id", "createTime")
                .eq("conversationId", conversationId)
                .orderByAsc("id")
                .last("limit 1"));
        return first != null && first.getCreateTime() != null
                && first.getCreateTime().getTime() < time.getTime() - RECORDING_GAP_MILLIS;
    }

    private static CompanionTraceTurnVO turnVO(CompanionTraceTurn turn, List<CompanionTraceStep> steps) {
        CompanionTraceTurnVO vo = new CompanionTraceTurnVO();
        vo.setId(turn.getId());
        vo.setType(turn.getType());
        vo.setStatus(turn.getStatus());
        List<TracePromptSection> sections = TracePrompts.split(turn.getUserPrompt());
        vo.setUserText(StringUtils.defaultIfBlank(turn.getUserText(), TracePrompts.userSays(sections)));
        vo.setContext(contextOf(sections));
        vo.setMessageId(turn.getMessageId());
        vo.setError(turn.getError());
        vo.setStartTime(turn.getStartTime());
        if (turn.getStartTime() != null && turn.getEndTime() != null) {
            vo.setDurationMs((int) Math.max(0, turn.getEndTime().getTime() - turn.getStartTime().getTime()));
        }
        // 按开始时间排：模型调用是回答完才写库的，写库的先后不一定是发生的先后
        vo.setSteps(steps.stream()
                .sorted(Comparator.comparing(CompanionTraceStep::getStartTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CompanionTraceStep::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CompanionTraceServiceImpl::stepVO)
                .toList());
        return vo;
    }

    private static List<TracePromptSection> contextOf(List<TracePromptSection> sections) {
        return sections.stream()
                .filter(section -> !MemoryTranscript.USER_SAYS.equals(section.getTitle()))
                .toList();
    }

    private static CompanionTraceStepVO stepVO(CompanionTraceStep step) {
        CompanionTraceStepVO vo = new CompanionTraceStepVO();
        vo.setId(step.getId());
        vo.setKind(step.getKind());
        vo.setName(step.getName());
        vo.setInput(step.getInput());
        vo.setOutput(step.getOutput());
        vo.setThinking(step.getThinking());
        vo.setToolCalls(TraceJson.read(step.getToolCalls(), TraceJson.TOOL_CALLS));
        vo.setInputTokens(step.getInputTokens());
        vo.setCachedTokens(step.getCachedTokens());
        vo.setOutputTokens(step.getOutputTokens());
        vo.setFinishReason(step.getFinishReason());
        vo.setError(step.getError());
        vo.setStartTime(step.getStartTime());
        vo.setDurationMs(step.getDurationMs());
        return vo;
    }

    /**
     * 没有记录的对话：把鸭鸭的记忆排成一轮一轮（没有耗时，整理过记忆的部分只剩整理笔记）
     */
    private void fromMemory(CompanionTraceVO vo, long conversationId) {
        List<ChatMessage> messages = chatMemoryStore.getMessages(new ConversationMemoryId(conversationId));
        if (messages == null || messages.isEmpty()) {
            return;
        }
        vo.setFromMemory(true);
        Integer systemIndex = null;
        CompanionTraceTurnVO turn = null;
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage system) {
                systemIndex = indexOf(vo.getSystemPrompts(), system.text());
            } else if (message instanceof UserMessage user) {
                String text = user.hasSingleText() ? user.singleText() : "";
                List<TracePromptSection> sections = TracePrompts.split(text);
                turn = new CompanionTraceTurnVO();
                turn.setType(text.startsWith(HINT_NOTE) ? CompanionMessageTypeEnum.HINT.getValue()
                        : text.startsWith(COMPACTION_NOTE) ? CompanionMessageTypeEnum.COMPACTION.getValue()
                        : CompanionMessageTypeEnum.CHAT.getValue());
                turn.setStatus(CompanionTraceTurn.STATUS_OK);
                turn.setUserText(TracePrompts.userSays(sections));
                turn.setContext(contextOf(sections));
                turn.setSystemPromptIndex(systemIndex);
                vo.getTurns().add(turn);
            } else if (turn != null && message instanceof AiMessage ai) {
                CompanionTraceStepVO step = new CompanionTraceStepVO();
                step.setKind(CompanionTraceStep.KIND_MODEL);
                step.setOutput(ai.text());
                if (ai.hasToolExecutionRequests()) {
                    step.setToolCalls(ai.toolExecutionRequests().stream()
                            .map(request -> new TraceToolCall(request.name(), request.arguments()))
                            .toList());
                }
                turn.getSteps().add(step);
            } else if (turn != null && message instanceof ToolExecutionResultMessage result) {
                CompanionTraceStepVO step = new CompanionTraceStepVO();
                step.setKind(CompanionTraceStep.KIND_TOOL);
                step.setName(result.toolName());
                step.setOutput(result.text());
                turn.getSteps().add(step);
            }
        }
    }

    // endregion
}
