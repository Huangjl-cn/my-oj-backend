package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjl.oj.companion.edit.CodeEditProposal;
import com.hjl.oj.mapper.CompanionMessageMapper;
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
import java.util.List;

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
            try {
                message.setCodeEdits(objectMapper.writeValueAsString(codeEdits));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("代码修改建议序列化失败", e);
            }
        }
        if (message.getCreateTime() == null) {
            message.setCreateTime(new Date());
        }
        this.save(message);
        return message;
    }

    @Override
    public List<CompanionMessage> listLatest(long userId, long questionId, Long beforeId, int limit) {
        List<CompanionMessage> latest = new ArrayList<>(this.list(new QueryWrapper<CompanionMessage>()
                .eq("userId", userId)
                .eq("questionId", questionId)
                .lt(beforeId != null, "id", beforeId)
                .orderByDesc("id")
                .last("limit " + limit)));
        Collections.reverse(latest);
        return latest;
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
    public List<CompanionMessage> reactionsSince(long userId, long questionId, Date since, int limit) {
        List<CompanionMessage> reactions = new ArrayList<>(this.list(new QueryWrapper<CompanionMessage>()
                .eq("userId", userId)
                .eq("questionId", questionId)
                .eq("type", CompanionMessageTypeEnum.REACTION.getValue())
                .gt(since != null, "createTime", since)
                .orderByDesc("id")
                .last("limit " + limit)));
        Collections.reverse(reactions);
        return reactions;
    }

    @Override
    public List<String> userQuestions(long userId, long questionId, int limit) {
        List<String> questions = new ArrayList<>(this.list(new QueryWrapper<CompanionMessage>()
                        .select("content")
                        .eq("userId", userId)
                        .eq("questionId", questionId)
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
        BeanUtils.copyProperties(message, vo, "codeEdits");
        if (message.getCodeEdits() != null) {
            try {
                vo.setCodeEdits(objectMapper.readValue(message.getCodeEdits(), CodeEditProposal.class));
            } catch (JsonProcessingException e) {
                log.warn("陪练消息的代码修改建议无法解析 messageId={}", message.getId());
            }
        }
        return vo;
    }
}
