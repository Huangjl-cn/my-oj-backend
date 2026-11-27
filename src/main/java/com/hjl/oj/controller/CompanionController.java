package com.hjl.oj.controller;

import com.hjl.oj.common.BaseResponse;
import com.hjl.oj.common.ResultUtils;
import com.hjl.oj.model.dto.companion.CompanionChatRequest;
import com.hjl.oj.model.dto.companion.CompanionCompactRequest;
import com.hjl.oj.model.dto.companion.CompanionDeleteConversationRequest;
import com.hjl.oj.model.dto.companion.CompanionHintRequest;
import com.hjl.oj.model.dto.companion.CompanionLevelRequest;
import com.hjl.oj.model.dto.companion.CompanionNewConversationRequest;
import com.hjl.oj.model.dto.companion.CompanionNoteApplyRequest;
import com.hjl.oj.model.dto.companion.CompanionReactRequest;
import com.hjl.oj.model.dto.companion.CompanionSolutionRequest;
import com.hjl.oj.model.dto.companion.CompanionSwitchConversationRequest;
import com.hjl.oj.model.vo.CompanionCompactionVO;
import com.hjl.oj.model.vo.CompanionConversationVO;
import com.hjl.oj.model.vo.CompanionHintStateVO;
import com.hjl.oj.model.vo.CompanionHintVO;
import com.hjl.oj.model.vo.CompanionMessageVO;
import com.hjl.oj.model.vo.CompanionNoteApplyVO;
import com.hjl.oj.model.vo.CompanionNotePreviewVO;
import com.hjl.oj.model.vo.CompanionReactionVO;
import com.hjl.oj.model.vo.CompanionSessionVO;
import com.hjl.oj.model.vo.CompanionTraceVO;
import com.hjl.oj.service.CompanionNoteService;
import com.hjl.oj.service.CompanionService;
import com.hjl.oj.service.CompanionTraceService;
import com.hjl.oj.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 陪练接口。流式接口（chat、solution）返回 SSE，事件说明见 CompanionSse；
 * 在开始推流前出错（未登录、没配 Key、请求进行中等）时返回普通的 JSON 错误响应。
 */
@RestController
@RequestMapping("/companion")
public class CompanionController {

    @Resource
    private CompanionService companionService;

    @Resource
    private CompanionNoteService companionNoteService;

    @Resource
    private CompanionTraceService companionTraceService;

    @Resource
    private UserService userService;

    @Operation(summary = "进入题目时获取陪练会话：提示档位、各档说明、当前对话的上下文占用和最近消息")
    @GetMapping("/session")
    public BaseResponse<CompanionSessionVO> getSession(@RequestParam long questionId, HttpServletRequest request) {
        return ResultUtils.success(companionService.getSession(userService.getLoginUser(request), questionId));
    }

    @Operation(summary = "往前翻对话里的历史消息")
    @GetMapping("/messages")
    public BaseResponse<List<CompanionMessageVO>> listMessages(@RequestParam long conversationId,
                                                               @RequestParam(required = false) Long beforeId,
                                                               HttpServletRequest request) {
        return ResultUtils.success(companionService.listMessages(userService.getLoginUser(request), conversationId,
                beforeId));
    }

    @Operation(summary = "本题的对话列表，最近活跃的在前")
    @GetMapping("/conversations")
    public BaseResponse<List<CompanionConversationVO>> listConversations(@RequestParam long questionId,
                                                                         HttpServletRequest request) {
        return ResultUtils.success(companionService.listConversations(userService.getLoginUser(request), questionId));
    }

    @Operation(summary = "开一段新对话（当前对话还没有消息时沿用它），返回切换后的会话")
    @PostMapping("/conversation")
    public BaseResponse<CompanionSessionVO> newConversation(@RequestBody CompanionNewConversationRequest newRequest,
                                                            HttpServletRequest request) {
        return ResultUtils.success(companionService.newConversation(userService.getLoginUser(request), newRequest));
    }

    @Operation(summary = "切换到之前的对话，返回切换后的会话")
    @PutMapping("/conversation/active")
    public BaseResponse<CompanionSessionVO> switchConversation(
            @RequestBody CompanionSwitchConversationRequest switchRequest, HttpServletRequest request) {
        return ResultUtils.success(companionService.switchConversation(userService.getLoginUser(request), switchRequest));
    }

    @Operation(summary = "删除一段对话：消息、记忆、轨迹一起删，删了找不回来；删的是当前对话时换成最近的另一段，返回删完后的会话")
    @PostMapping("/conversation/delete")
    public BaseResponse<CompanionSessionVO> deleteConversation(
            @RequestBody CompanionDeleteConversationRequest deleteRequest, HttpServletRequest request) {
        return ResultUtils.success(companionService.deleteConversation(userService.getLoginUser(request), deleteRequest));
    }

    @Operation(summary = "一段对话的轨迹：每一轮发给模型的内容、模型调用、工具调用、耗时和 token")
    @GetMapping("/trace")
    public BaseResponse<CompanionTraceVO> getTrace(@RequestParam long conversationId, HttpServletRequest request) {
        return ResultUtils.success(companionTraceService.getTrace(userService.getLoginUser(request), conversationId));
    }

    @Operation(summary = "判题结束后获取陪练的反应（同一次提交重复调用返回同一结果）")
    @PostMapping("/react")
    public BaseResponse<CompanionReactionVO> react(@RequestBody CompanionReactRequest reactRequest,
                                                   HttpServletRequest request) {
        return ResultUtils.success(companionService.react(userService.getLoginUser(request), reactRequest));
    }

    @Operation(summary = "和陪练聊天（SSE 流式）")
    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody CompanionChatRequest chatRequest, HttpServletRequest request) {
        return companionService.chat(userService.getLoginUser(request), chatRequest);
    }

    @Operation(summary = "按当前档位给一条提示")
    @PostMapping("/hint")
    public BaseResponse<CompanionHintVO> hint(@RequestBody CompanionHintRequest hintRequest, HttpServletRequest request) {
        return ResultUtils.success(companionService.hint(userService.getLoginUser(request), hintRequest));
    }

    @Operation(summary = "看答案：参考代码和讲解（SSE 流式，含思考过程）")
    @PostMapping("/solution")
    public SseEmitter solution(@RequestBody CompanionSolutionRequest solutionRequest, HttpServletRequest request) {
        return companionService.solution(userService.getLoginUser(request), solutionRequest);
    }

    @Operation(summary = "设置本题的提示档位 0-4")
    @PutMapping("/level")
    public BaseResponse<CompanionHintStateVO> setLevel(@RequestBody CompanionLevelRequest levelRequest,
                                                       HttpServletRequest request) {
        return ResultUtils.success(companionService.setLevel(userService.getLoginUser(request), levelRequest));
    }

    @Operation(summary = "预览鸭鸭的一条笔记提议：按笔记现在的内容算出应用后的样子")
    @GetMapping("/note/preview")
    public BaseResponse<CompanionNotePreviewVO> previewNoteEdit(@RequestParam long messageId, @RequestParam int index,
                                                                HttpServletRequest request) {
        return ResultUtils.success(companionNoteService.preview(userService.getLoginUser(request), messageId, index));
    }

    @Operation(summary = "应用鸭鸭的一条笔记提议：新建或修改笔记，并标记为已应用")
    @PostMapping("/note/apply")
    public BaseResponse<CompanionNoteApplyVO> applyNoteEdit(@RequestBody CompanionNoteApplyRequest applyRequest,
                                                            HttpServletRequest request) {
        return ResultUtils.success(companionNoteService.apply(userService.getLoginUser(request), applyRequest));
    }

    @Operation(summary = "手动整理记忆")
    @PostMapping("/compact")
    public BaseResponse<CompanionCompactionVO> compact(@RequestBody CompanionCompactRequest compactRequest,
                                                       HttpServletRequest request) {
        return ResultUtils.success(companionService.compact(userService.getLoginUser(request), compactRequest));
    }
}
