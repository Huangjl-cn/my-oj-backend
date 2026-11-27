package com.hjl.oj.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.entity.CompanionConversation;
import com.hjl.oj.model.entity.CompanionSession;
import com.hjl.oj.model.vo.CompanionConversationVO;

import java.util.List;

/**
 * 陪练对话：同一道题下可以开多段对话，会话记录当前是哪一段；模型记忆由 MysqlChatMemoryStore 读写
 */
public interface CompanionConversationService extends IService<CompanionConversation> {

    /**
     * 会话的当前对话，还没有时创建（不查记忆列）
     */
    CompanionConversation getActive(CompanionSession session);

    /**
     * 取用户自己的对话（不查记忆列），不存在或不属于该用户时抛 NOT_FOUND
     */
    CompanionConversation requireOwned(long userId, long conversationId);

    /**
     * 开一段新对话并设为当前；当前对话还没有任何消息时直接沿用它，不再新建
     */
    CompanionConversation startNew(CompanionSession session);

    /**
     * 当前对话被删掉了：换成本题最近活跃的另一段，没有就开一段新的
     */
    CompanionConversation activateLatest(CompanionSession session);

    /**
     * 把某段对话设为当前；原来的当前对话如果没有任何消息就删掉
     */
    void activate(CompanionSession session, CompanionConversation conversation);

    /**
     * 本题有消息的对话和当前对话，最近活跃的在前
     */
    List<CompanionConversationVO> listForQuestion(CompanionSession session);

    void updateContextTokens(long conversationId, int contextTokens);

    /**
     * 对话还没有标题时，用这句话的开头作为标题
     */
    void titleIfAbsent(long conversationId, String text);
}
