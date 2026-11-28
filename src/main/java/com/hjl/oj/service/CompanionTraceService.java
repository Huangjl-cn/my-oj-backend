package com.hjl.oj.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.companion.trace.TraceRecorder;
import com.hjl.oj.companion.trace.TraceTool;
import com.hjl.oj.model.entity.CompanionTraceStep;
import com.hjl.oj.model.entity.CompanionTraceTurn;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.CompanionTraceVO;

import java.util.List;

/**
 * 鸭鸭的轨迹：记录每一轮的模型调用和工具调用，供用户回看。记录失败不影响鸭鸭本身。
 */
public interface CompanionTraceService extends IService<CompanionTraceTurn> {

    /**
     * 开始一轮；返回这一轮的记录器（挂到模型上收调用，工具执行完交给它，结束时调 finish / fail）
     *
     * @param type 见 CompanionMessageTypeEnum：chat / hint / solution / reaction / compaction
     */
    TraceRecorder startTurn(long userId, long questionId, long conversationId, String type, String userText);

    /**
     * 记下这一轮发给模型的系统提示词、用户消息和工具；系统提示词、工具和这段对话上一轮一样时不重复存
     */
    void describeTurn(long turnId, long conversationId, String systemPrompt, String userPrompt, List<TraceTool> tools);

    void saveStep(CompanionTraceStep step);

    void finishTurn(long turnId, String status, Long messageId, String error);

    /**
     * 这一轮其实什么也没做（比如整理记忆时对话太短），删掉
     */
    void discardTurn(long turnId);

    void deleteByConversation(long conversationId);

    /**
     * 用户自己的一段对话的轨迹；没有记录时从鸭鸭的记忆还原
     */
    CompanionTraceVO getTrace(User loginUser, long conversationId);
}
