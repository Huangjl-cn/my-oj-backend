package com.hjl.oj.service;

import com.hjl.oj.model.dto.companion.CompanionChatRequest;
import com.hjl.oj.model.dto.companion.CompanionCompactRequest;
import com.hjl.oj.model.dto.companion.CompanionHintRequest;
import com.hjl.oj.model.dto.companion.CompanionLevelRequest;
import com.hjl.oj.model.dto.companion.CompanionNewConversationRequest;
import com.hjl.oj.model.dto.companion.CompanionReactRequest;
import com.hjl.oj.model.dto.companion.CompanionSolutionRequest;
import com.hjl.oj.model.dto.companion.CompanionSwitchConversationRequest;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.CompanionCompactionVO;
import com.hjl.oj.model.vo.CompanionConversationVO;
import com.hjl.oj.model.vo.CompanionHintStateVO;
import com.hjl.oj.model.vo.CompanionHintVO;
import com.hjl.oj.model.vo.CompanionMessageVO;
import com.hjl.oj.model.vo.CompanionReactionVO;
import com.hjl.oj.model.vo.CompanionSessionVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 陪练：编排会话与对话、提示、提交反应、聊天、看答案和记忆整理
 */
public interface CompanionService {

    /**
     * 进入题目：提示档位、各档说明、当前对话的上下文占用和最近消息
     */
    CompanionSessionVO getSession(User loginUser, long questionId);

    /**
     * 往前翻历史：对话里 beforeId 之前的消息，按时间正序
     */
    List<CompanionMessageVO> listMessages(User loginUser, long conversationId, Long beforeId);

    List<CompanionConversationVO> listConversations(User loginUser, long questionId);

    /**
     * 开一段新对话，返回切换后的会话
     */
    CompanionSessionVO newConversation(User loginUser, CompanionNewConversationRequest request);

    /**
     * 切换到之前的对话，返回切换后的会话
     */
    CompanionSessionVO switchConversation(User loginUser, CompanionSwitchConversationRequest request);

    /**
     * 判题结束后的反应，记在当前对话里。同一次提交重复调用返回第一次的结果；没配 Key 或模型调用失败时返回兜底话术
     */
    CompanionReactionVO react(User loginUser, CompanionReactRequest request);

    SseEmitter chat(User loginUser, CompanionChatRequest request);

    /**
     * 按当前档位给一条提示，不改变档位
     */
    CompanionHintVO hint(User loginUser, CompanionHintRequest request);

    SseEmitter solution(User loginUser, CompanionSolutionRequest request);

    CompanionHintStateVO setLevel(User loginUser, CompanionLevelRequest request);

    CompanionCompactionVO compact(User loginUser, CompanionCompactRequest request);
}
