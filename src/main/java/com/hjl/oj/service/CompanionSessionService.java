package com.hjl.oj.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.entity.CompanionSession;
import com.hjl.oj.model.enums.HintTrackEnum;

/**
 * 陪练会话：每个用户在每道题下一行，记录提示进度和上下文占用（模型记忆由 MysqlChatMemoryStore 读写）
 */
public interface CompanionSessionService extends IService<CompanionSession> {

    /**
     * 取会话，不存在时创建（不查记忆列）
     */
    CompanionSession getOrCreate(long userId, long questionId);

    void updateHintState(long sessionId, int hintLevel, HintTrackEnum hintTrack);

    void updateHintTrack(long sessionId, HintTrackEnum hintTrack);

    void updateContextTokens(long sessionId, int contextTokens);
}
