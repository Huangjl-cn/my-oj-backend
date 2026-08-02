package com.hjl.oj.judge.codesandbox.impl;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.CodeSandbox;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 远程代码沙箱（实际调用接口的沙箱）
 */
@Component
public class RemoteCodeSandbox implements CodeSandbox {

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Value("${codesandbox.url}")
    private String codesandboxUrl;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String language = executeCodeRequest.getLanguage();

        // 判断是否为 Java 语言
        if (QuestionSubmitLanguageEnum.JAVA.getValue().equals(language)) {
            System.out.println("调用 Java 代码沙箱接口");
            return executeByPath(executeCodeRequest, "/executeCode");
        } else {
            // 其他语言调用 AI 代码沙箱接口
            System.out.println("调用 AI 代码沙箱接口，语言：" + language);
            return executeByPath(executeCodeRequest, "/executeCodeByAI");
        }
    }

    /**
     * 根据路径调用代码沙箱接口
     *
     * @param executeCodeRequest 执行请求
     * @param path               请求路径
     * @return 执行结果
     */
    private ExecuteCodeResponse executeByPath(ExecuteCodeRequest executeCodeRequest, String path) {
        String url = codesandboxUrl + path;
        String json = JSONUtil.toJsonStr(executeCodeRequest);

        // 使用 try-with-resources 确保 HTTP 连接资源被正确关闭
        try (HttpResponse response = HttpUtil.createPost(url)
                .header(AUTH_REQUEST_HEADER, AUTH_REQUEST_SECRET)
                .body(json)
                .execute()) {

            String responseStr = response.body();
            if (StringUtils.isBlank(responseStr)) {
                throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "executeCode error, path=" + path + ", message = " + responseStr);
            }
            return JSONUtil.toBean(responseStr, ExecuteCodeResponse.class);
        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            // 其他异常包装为业务异常
            throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "executeCode error, path=" + path + ": " + e.getMessage());
        }
    }
}
