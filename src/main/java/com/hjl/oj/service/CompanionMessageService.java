package com.hjl.oj.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.companion.edit.CodeEditProposal;
import com.hjl.oj.model.dto.companion.CodeQuote;
import com.hjl.oj.model.entity.CompanionConversation;
import com.hjl.oj.model.entity.CompanionMessage;
import com.hjl.oj.model.enums.CompanionMessageTypeEnum;
import com.hjl.oj.model.vo.CompanionMessageVO;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 陪练消息（给前端展示的对话历史）
 */
public interface CompanionMessageService extends IService<CompanionMessage> {

    /**
     * 保存一条消息，附带的修改建议序列化为 JSON
     */
    CompanionMessage saveMessage(CompanionMessage message, CodeEditProposal codeEdits);

    /**
     * 保存用户说的一句话，quote 为引用的代码，可为空
     */
    CompanionMessage saveUserMessage(CompanionConversation conversation, CompanionMessageTypeEnum type, String content,
                                     CodeQuote quote);

    /**
     * 取对话里 beforeId 之前（不含）最近的 limit 条，按时间正序返回；beforeId 为空时取最新的
     */
    List<CompanionMessage> listLatest(long conversationId, Long beforeId, int limit);

    boolean hasMessages(long conversationId);

    /**
     * 本题每段对话的消息数，key 为对话 id，没有消息的对话不在其中
     */
    Map<Long, Long> countByConversation(long userId, long questionId);

    CompanionMessage findReaction(long userId, long submissionId);

    /**
     * 本题最近几次提交后陪练说过的话（新的在前，不分对话），用来避免重复
     */
    List<String> recentReactionLines(long userId, long questionId, int limit);

    /**
     * 对话里 since 之后的提交反应，按时间正序；since 为空时取最近的 limit 条
     */
    List<CompanionMessage> reactionsSince(long conversationId, Date since, int limit);

    /**
     * 用户在这段对话里说过的话，按时间正序
     */
    List<String> userQuestions(long conversationId, int limit);

    CompanionMessageVO toVO(CompanionMessage message);
}
