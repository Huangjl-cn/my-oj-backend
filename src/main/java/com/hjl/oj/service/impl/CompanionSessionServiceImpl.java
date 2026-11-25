package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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
    public void updateActiveConversation(long sessionId, long conversationId) {
        this.update(new UpdateWrapper<CompanionSession>()
                .eq("id", sessionId)
                .set("activeConversationId", conversationId));
    }

    @Override
    public boolean claimActiveConversation(long sessionId, long conversationId) {
        return this.update(new UpdateWrapper<CompanionSession>()
                .eq("id", sessionId)
                .isNull("activeConversationId")
                .set("activeConversationId", conversationId));
    }

    private CompanionSession find(long userId, long questionId) {
        return this.getOne(new QueryWrapper<CompanionSession>()
                .eq("userId", userId)
                .eq("questionId", questionId));
    }
}
