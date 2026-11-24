package com.hjl.oj.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.companion.edit.CodeEditProposal;
import com.hjl.oj.model.entity.CompanionMessage;
import com.hjl.oj.model.vo.CompanionMessageVO;

import java.util.Date;
import java.util.List;

/**
 * 陪练消息（给前端展示的对话历史）
 */
public interface CompanionMessageService extends IService<CompanionMessage> {

    /**
     * 保存一条消息，附带的修改建议序列化为 JSON
     */
    CompanionMessage saveMessage(CompanionMessage message, CodeEditProposal codeEdits);

    /**
     * 取 beforeId 之前（不含）最近的 limit 条，按时间正序返回；beforeId 为空时取最新的
     */
    List<CompanionMessage> listLatest(long userId, long questionId, Long beforeId, int limit);

    CompanionMessage findReaction(long userId, long submissionId);

    /**
     * 最近几次提交后陪练说过的话（新的在前），用来避免重复
     */
    List<String> recentReactionLines(long userId, long questionId, int limit);

    /**
     * since 之后的提交反应，按时间正序；since 为空时取最近的 limit 条
     */
    List<CompanionMessage> reactionsSince(long userId, long questionId, Date since, int limit);

    /**
     * 用户在本题聊天时说过的话，按时间正序
     */
    List<String> userQuestions(long userId, long questionId, int limit);

    CompanionMessageVO toVO(CompanionMessage message);
}
