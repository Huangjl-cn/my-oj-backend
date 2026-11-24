package com.hjl.oj.controller;

import com.hjl.oj.common.BaseResponse;
import com.hjl.oj.common.ResultUtils;
import com.hjl.oj.model.dto.companion.CompanionChatRequest;
import com.hjl.oj.model.dto.companion.CompanionCompactRequest;
import com.hjl.oj.model.dto.companion.CompanionHintRequest;
import com.hjl.oj.model.dto.companion.CompanionLevelRequest;
import com.hjl.oj.model.dto.companion.CompanionReactRequest;
import com.hjl.oj.model.dto.companion.CompanionSolutionRequest;
import com.hjl.oj.model.vo.CompanionCompactionVO;
import com.hjl.oj.model.vo.CompanionHintStateVO;
import com.hjl.oj.model.vo.CompanionHintVO;
import com.hjl.oj.model.vo.CompanionMessageVO;
import com.hjl.oj.model.vo.CompanionReactionVO;
import com.hjl.oj.model.vo.CompanionSessionVO;
import com.hjl.oj.service.CompanionService;
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
    private UserService userService;

    @Operation(summary = "进入题目时获取陪练会话：提示状态、步骤名称、上下文占用、最近消息")
    @GetMapping("/session")
    public BaseResponse<CompanionSessionVO> getSession(@RequestParam long questionId, HttpServletRequest request) {
        return ResultUtils.success(companionService.getSession(userService.getLoginUser(request), questionId));
    }

    @Operation(summary = "往前翻历史消息")
    @GetMapping("/messages")
    public BaseResponse<List<CompanionMessageVO>> listMessages(@RequestParam long questionId,
                                                               @RequestParam(required = false) Long beforeId,
                                                               HttpServletRequest request) {
        return ResultUtils.success(companionService.listMessages(userService.getLoginUser(request), questionId, beforeId));
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

    @Operation(summary = "再给点提示（分级提示 1-4 级）")
    @PostMapping("/hint")
    public BaseResponse<CompanionHintVO> hint(@RequestBody CompanionHintRequest hintRequest, HttpServletRequest request) {
        return ResultUtils.success(companionService.hint(userService.getLoginUser(request), hintRequest));
    }

    @Operation(summary = "看答案：参考代码和讲解（SSE 流式，含思考过程）")
    @PostMapping("/solution")
    public SseEmitter solution(@RequestBody CompanionSolutionRequest solutionRequest, HttpServletRequest request) {
        return companionService.solution(userService.getLoginUser(request), solutionRequest);
    }

    @Operation(summary = "直接设置本题的提示等级（0 表示从头自己做）")
    @PutMapping("/level")
    public BaseResponse<CompanionHintStateVO> setLevel(@RequestBody CompanionLevelRequest levelRequest,
                                                       HttpServletRequest request) {
        return ResultUtils.success(companionService.setLevel(userService.getLoginUser(request), levelRequest));
    }

    @Operation(summary = "手动整理记忆")
    @PostMapping("/compact")
    public BaseResponse<CompanionCompactionVO> compact(@RequestBody CompanionCompactRequest compactRequest,
                                                       HttpServletRequest request) {
        return ResultUtils.success(companionService.compact(userService.getLoginUser(request), compactRequest));
    }
}
