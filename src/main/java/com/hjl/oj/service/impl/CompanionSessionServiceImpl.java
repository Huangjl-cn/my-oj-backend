package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hjl.oj.mapper.CompanionSessionMapper;
import com.hjl.oj.model.entity.CompanionSession;
import com.hjl.oj.model.enums.HintTrackEnum;
import com.hjl.oj.service.CompanionSessionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 陪练会话
 */
@Service
public class CompanionSessionServiceImpl extends ServiceImpl<CompanionSessionMapper, CompanionSession>
        implements CompanionSessionService {

    /**
     * 除记忆外的列：记忆可能很大，只在 MysqlChatMemoryStore 里读
     */
    private static final String[] STATE_COLUMNS = {
            "id", "userId", "questionId", "hintLevel", "hintTrack", "contextTokens", "memoryUpdateTime",
            "createTime", "updateTime"};

    @Override
    public CompanionSession getOrCreate(long userId, long questionId) {
        CompanionSession existing = find(userId, questionId);
        if (existing != null) {
            return existing;
        }
        CompanionSession session = new CompanionSession();
        session.setUserId(userId);
        session.setQuestionId(questionId);
        session.setHintLevel(0);
        session.setHintTrack(HintTrackEnum.IDEA.getValue());
        session.setContextTokens(0);
        try {
            this.save(session);
        } catch (DuplicateKeyException e) {
            // 同一用户同一题并发首次进入：另一请求已创建
            return find(userId, questionId);
        }
        return find(userId, questionId);
    }

    @Override
    public void updateHintState(long sessionId, int hintLevel, HintTrackEnum hintTrack) {
        this.update(new UpdateWrapper<CompanionSession>()
                .eq("id", sessionId)
                .set("hintLevel", hintLevel)
                .set("hintTrack", hintTrack.getValue()));
    }

    @Override
    public void updateHintTrack(long sessionId, HintTrackEnum hintTrack) {
        this.update(new UpdateWrapper<CompanionSession>()
                .eq("id", sessionId)
                .set("hintTrack", hintTrack.getValue()));
    }

    @Override
    public void updateContextTokens(long sessionId, int contextTokens) {
        this.update(new UpdateWrapper<CompanionSession>()
                .eq("id", sessionId)
                .set("contextTokens", Math.max(0, contextTokens)));
    }

    private CompanionSession find(long userId, long questionId) {
        return this.getOne(new QueryWrapper<CompanionSession>()
                .select(STATE_COLUMNS)
                .eq("userId", userId)
                .eq("questionId", questionId));
    }
}
