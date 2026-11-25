package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.mapper.CompanionConversationMapper;
import com.hjl.oj.model.entity.CompanionConversation;
import com.hjl.oj.model.entity.CompanionSession;
import com.hjl.oj.model.vo.CompanionConversationVO;
import com.hjl.oj.service.CompanionConversationService;
import com.hjl.oj.service.CompanionMessageService;
import com.hjl.oj.service.CompanionSessionService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 陪练对话
 */
@Service
public class CompanionConversationServiceImpl extends ServiceImpl<CompanionConversationMapper, CompanionConversation>
        implements CompanionConversationService {

    static final int TITLE_CHARS = 20;

    /**
     * 除记忆外的列：记忆可能很大，只在 MysqlChatMemoryStore 里读
     */
    private static final String[] STATE_COLUMNS = {
            "id", "userId", "questionId", "title", "contextTokens", "memoryUpdateTime", "createTime", "updateTime"};

    private final CompanionSessionService sessionService;

    private final CompanionMessageService messageService;

    public CompanionConversationServiceImpl(CompanionSessionService sessionService,
                                            CompanionMessageService messageService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
    }

    @Override
    public CompanionConversation getActive(CompanionSession session) {
        Long activeId = session.getActiveConversationId();
        if (activeId != null) {
            CompanionConversation active = findState(activeId);
            if (active != null) {
                return active;
            }
        }
        CompanionConversation created = create(session);
        if (activeId != null) {
            sessionService.updateActiveConversation(session.getId(), created.getId());
        } else if (!sessionService.claimActiveConversation(session.getId(), created.getId())) {
            // 同一用户同一题并发首次进入：另一请求已设置了当前对话，用它的
            this.removeById(created.getId());
            CompanionSession latest = sessionService.getById(session.getId());
            session.setActiveConversationId(latest.getActiveConversationId());
            return findState(latest.getActiveConversationId());
        }
        session.setActiveConversationId(created.getId());
        return created;
    }

    @Override
    public CompanionConversation requireOwned(long userId, long conversationId) {
        CompanionConversation conversation = findState(conversationId);
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "对话不存在");
        }
        return conversation;
    }

    @Override
    public CompanionConversation startNew(CompanionSession session) {
        CompanionConversation active = getActive(session);
        if (!messageService.hasMessages(active.getId())) {
            return active;
        }
        CompanionConversation created = create(session);
        sessionService.updateActiveConversation(session.getId(), created.getId());
        session.setActiveConversationId(created.getId());
        return created;
    }

    @Override
    public void activate(CompanionSession session, CompanionConversation conversation) {
        Long previousId = session.getActiveConversationId();
        if (Objects.equals(previousId, conversation.getId())) {
            return;
        }
        sessionService.updateActiveConversation(session.getId(), conversation.getId());
        session.setActiveConversationId(conversation.getId());
        if (previousId != null && !messageService.hasMessages(previousId)) {
            this.removeById(previousId);
        }
    }

    @Override
    public List<CompanionConversationVO> listForQuestion(CompanionSession session) {
        Map<Long, Long> counts = messageService.countByConversation(session.getUserId(), session.getQuestionId());
        return this.list(new QueryWrapper<CompanionConversation>()
                        .select(STATE_COLUMNS)
                        .eq("userId", session.getUserId())
                        .eq("questionId", session.getQuestionId())
                        .orderByDesc("updateTime", "id"))
                .stream()
                .filter(conversation -> counts.containsKey(conversation.getId())
                        || Objects.equals(conversation.getId(), session.getActiveConversationId()))
                .map(conversation -> {
                    CompanionConversationVO vo = new CompanionConversationVO();
                    vo.setId(conversation.getId());
                    vo.setTitle(conversation.getTitle());
                    vo.setMessageCount(counts.getOrDefault(conversation.getId(), 0L));
                    vo.setActive(Objects.equals(conversation.getId(), session.getActiveConversationId()));
                    vo.setCreateTime(conversation.getCreateTime());
                    vo.setUpdateTime(conversation.getUpdateTime());
                    return vo;
                })
                .toList();
    }

    @Override
    public void updateContextTokens(long conversationId, int contextTokens) {
        this.update(new UpdateWrapper<CompanionConversation>()
                .eq("id", conversationId)
                .set("contextTokens", Math.max(0, contextTokens)));
    }

    @Override
    public void titleIfAbsent(long conversationId, String text) {
        String title = toTitle(text);
        if (title.isEmpty()) {
            return;
        }
        this.update(new UpdateWrapper<CompanionConversation>()
                .eq("id", conversationId)
                .isNull("title")
                .set("title", title));
    }

    static String toTitle(String text) {
        String flat = StringUtils.normalizeSpace(StringUtils.defaultString(text));
        return flat.length() > TITLE_CHARS ? flat.substring(0, TITLE_CHARS) + "…" : flat;
    }

    private CompanionConversation create(CompanionSession session) {
        CompanionConversation conversation = new CompanionConversation();
        conversation.setUserId(session.getUserId());
        conversation.setQuestionId(session.getQuestionId());
        conversation.setContextTokens(0);
        this.save(conversation);
        return findState(conversation.getId());
    }

    private CompanionConversation findState(long conversationId) {
        return this.getOne(new QueryWrapper<CompanionConversation>()
                .select(STATE_COLUMNS)
                .eq("id", conversationId));
    }
}
