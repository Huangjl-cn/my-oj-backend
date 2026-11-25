package com.hjl.oj.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.entity.CompanionSession;
import com.hjl.oj.model.enums.HintTrackEnum;

/**
 * 陪练会话：每个用户在每道题下一行，记录提示档位、陪练判断的方向和当前对话
 */
public interface CompanionSessionService extends IService<CompanionSession> {

    /**
     * 取会话，不存在时创建（此时还没有当前对话）
     */
    CompanionSession getOrCreate(long userId, long questionId);

    void updateHintState(long sessionId, int hintLevel, HintTrackEnum hintTrack);

    void updateHintTrack(long sessionId, HintTrackEnum hintTrack);

    void updateActiveConversation(long sessionId, long conversationId);

    /**
     * 只在会话还没有当前对话时设置，返回是否设置成功（并发首次进入时只有一个请求能成功）
     */
    boolean claimActiveConversation(long sessionId, long conversationId);
}
