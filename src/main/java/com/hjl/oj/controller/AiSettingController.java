package com.hjl.oj.controller;

import com.hjl.oj.common.BaseResponse;
import com.hjl.oj.common.ResultUtils;
import com.hjl.oj.model.dto.ai.AiSettingSaveRequest;
import com.hjl.oj.model.vo.AiSettingVO;
import com.hjl.oj.service.AiSettingService;
import com.hjl.oj.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户 AI 设置：自带的 DeepSeek API Key（加密存储，接口只返回后 4 位）、模型、提示模式
 */
@RestController
@RequestMapping("/ai/setting")
public class AiSettingController {

    @Resource
    private AiSettingService aiSettingService;

    @Resource
    private UserService userService;

    @Operation(summary = "获取当前用户的 AI 设置")
    @GetMapping
    public BaseResponse<AiSettingVO> getSetting(HttpServletRequest request) {
        return ResultUtils.success(aiSettingService.getSettingVO(userService.getLoginUser(request)));
    }

    @Operation(summary = "保存 AI 设置；带了新 Key 时先校验再加密保存")
    @PutMapping
    public BaseResponse<AiSettingVO> saveSetting(@RequestBody AiSettingSaveRequest saveRequest, HttpServletRequest request) {
        return ResultUtils.success(aiSettingService.saveSetting(userService.getLoginUser(request), saveRequest));
    }

    @Operation(summary = "删除保存的 API Key")
    @DeleteMapping("/key")
    public BaseResponse<AiSettingVO> deleteKey(HttpServletRequest request) {
        return ResultUtils.success(aiSettingService.deleteKey(userService.getLoginUser(request)));
    }
}
