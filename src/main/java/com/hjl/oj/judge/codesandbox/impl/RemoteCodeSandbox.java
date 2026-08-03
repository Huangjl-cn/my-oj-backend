package com.hjl.oj.judge.codesandbox.impl;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.CodeSandbox;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeResponse;
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

    private static final String EXECUTE_CODE_PATH = "/executeCode";

    @Value("${codesandbox.url}")
    private String codesandboxUrl;

    @Value("${codesandbox.timeout:60000}")
    private int timeout;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String url = codesandboxUrl + EXECUTE_CODE_PATH;
        String json = JSONUtil.toJsonStr(executeCodeRequest);

        // 使用 try-with-resources 确保 HTTP 连接资源被正确关闭
        try (HttpResponse response = HttpUtil.createPost(url)
                .header(AUTH_REQUEST_HEADER, AUTH_REQUEST_SECRET)
                .body(json)
                .timeout(timeout)
                .execute()) {

            String responseStr = response.body();
            if (StringUtils.isBlank(responseStr)) {
                throw new BusinessException(ErrorCode.API_REQUEST_ERROR,
                        "executeCode error, path=" + EXECUTE_CODE_PATH + ", message = " + responseStr);
            }
            return JSONUtil.toBean(responseStr, ExecuteCodeResponse.class);
        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            // 其他异常包装为业务异常
            throw new BusinessException(ErrorCode.API_REQUEST_ERROR,
                    "executeCode error, path=" + EXECUTE_CODE_PATH + ": " + e.getMessage());
        }
    }
}
