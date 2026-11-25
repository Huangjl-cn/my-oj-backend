package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjl.oj.companion.edit.CodeEditProposal;
import com.hjl.oj.mapper.CompanionMessageMapper;
import com.hjl.oj.model.dto.companion.CodeQuote;
import com.hjl.oj.model.entity.CompanionConversation;
import com.hjl.oj.model.entity.CompanionMessage;
import com.hjl.oj.model.enums.CompanionMessageTypeEnum;
import com.hjl.oj.model.enums.CompanionRoleEnum;
import com.hjl.oj.model.vo.CompanionMessageVO;
import com.hjl.oj.service.CompanionMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 陪练消息
 */
@Slf4j
@Service
public class CompanionMessageServiceImpl extends ServiceImpl<CompanionMessageMapper, CompanionMessage>
        implements CompanionMessageService {

    private final ObjectMapper objectMapper;

    public CompanionMessageServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CompanionMessage saveMessage(CompanionMessage message, CodeEditProposal codeEdits) {
        if (codeEdits != null) {
            message.setCodeEdits(toJson(codeEdits));
        }
        if (message.getCreateTime() == null) {
            message.setCreateTime(new Date());
        }
        this.save(message);
        return message;
    }

    @Override
    public CompanionMessage saveUserMessage(CompanionConversation conversation, CompanionMessageTypeEnum type,
                                            String content, CodeQuote quote) {
        CompanionMessage message = new CompanionMessage();
        message.setUserId(conversation.getUserId());
        message.setQuestionId(conversation.getQuestionId());
        message.setConversationId(conversation.getId());
        message.setRole(CompanionRoleEnum.USER.getValue());
        message.setType(type.getValue());
        message.setContent(content);
        if (quote != null) {
            message.setQuote(toJson(quote));
        }
        return saveMessage(message, null);
    }

    @Override
    public List<CompanionMessage> listLatest(long conversationId, Long beforeId, int limit) {
        List<CompanionMessage> latest = new ArrayList<>(this.list(new QueryWrapper<CompanionMessage>()
                .eq("conversationId", conversationId)
                .lt(beforeId != null, "id", beforeId)
                .orderByDesc("id")
                .last("limit " + limit)));
        Collections.reverse(latest);
        return latest;
    }

    @Override
    public boolean hasMessages(long conversationId) {
        return this.count(new QueryWrapper<CompanionMessage>().eq("conversationId", conversationId)) > 0;
    }

    @Override
    public Map<Long, Long> countByConversation(long userId, long questionId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : this.listMaps(new QueryWrapper<CompanionMessage>()
                .select("conversationId", "count(*) as messageCount")
                .eq("userId", userId)
                .eq("questionId", questionId)
                .groupBy("conversationId"))) {
            counts.put(((Number) row.get("conversationId")).longValue(), ((Number) row.get("messageCount")).longValue());
        }
        return counts;
    }

    @Override
    public CompanionMessage findReaction(long userId, long submissionId) {
        return this.getOne(new QueryWrapper<CompanionMessage>()
                .eq("userId", userId)
                .eq("submissionId", submissionId)
                .eq("type", CompanionMessageTypeEnum.REACTION.getValue())
                .orderByDesc("id")
                .last("limit 1"));
    }

    @Override
    public List<String> recentReactionLines(long userId, long questionId, int limit) {
        return this.list(new QueryWrapper<CompanionMessage>()
                        .select("content")
                        .eq("userId", userId)
                        .eq("questionId", questionId)
                        .eq("type", CompanionMessageTypeEnum.REACTION.getValue())
                        .orderByDesc("id")
                        .last("limit " + limit))
                .stream()
                .map(CompanionMessage::getContent)
                .toList();
    }

    @Override
    public List<CompanionMessage> reactionsSince(long conversationId, Date since, int limit) {
        List<CompanionMessage> reactions = new ArrayList<>(this.list(new QueryWrapper<CompanionMessage>()
                .eq("conversationId", conversationId)
                .eq("type", CompanionMessageTypeEnum.REACTION.getValue())
                .gt(since != null, "createTime", since)
                .orderByDesc("id")
                .last("limit " + limit)));
        Collections.reverse(reactions);
        return reactions;
    }

    @Override
    public List<String> userQuestions(long conversationId, int limit) {
        List<String> questions = new ArrayList<>(this.list(new QueryWrapper<CompanionMessage>()
                        .select("content")
                        .eq("conversationId", conversationId)
                        .eq("role", CompanionRoleEnum.USER.getValue())
                        .eq("type", CompanionMessageTypeEnum.CHAT.getValue())
                        .orderByDesc("id")
                        .last("limit " + limit))
                .stream()
                .map(CompanionMessage::getContent)
                .toList());
        Collections.reverse(questions);
        return questions;
    }

    @Override
    public CompanionMessageVO toVO(CompanionMessage message) {
        CompanionMessageVO vo = new CompanionMessageVO();
        BeanUtils.copyProperties(message, vo, "codeEdits", "quote");
        vo.setCodeEdits(fromJson(message.getCodeEdits(), CodeEditProposal.class, message.getId()));
        vo.setQuote(fromJson(message.getQuote(), CodeQuote.class, message.getId()));
        return vo;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("陪练消息附带内容序列化失败", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type, Long messageId) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("陪练消息附带的 {} 无法解析 messageId={}", type.getSimpleName(), messageId);
            return null;
        }
    }
}
