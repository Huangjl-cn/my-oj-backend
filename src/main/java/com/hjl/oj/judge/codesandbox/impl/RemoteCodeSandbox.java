package com.hjl.oj.judge.codesandbox.impl;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.CodeSandbox;
import com.hjl.oj.judge.codesandbox.auth.SandboxAuthSigner;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeResponse;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 远程代码沙箱（实际调用接口的沙箱）
 *
 * <p>调用鉴权采用 ECDSA 请求签名（见 {@link SandboxAuthSigner}）：
 * 每个请求携带 key-id / timestamp / nonce / 签名四个请求头，
 * 沙箱侧验签、校验时间窗并拒绝重复 nonce（协议细节见 docs/sandbox-auth/README.md）。
 */
@Component
public class RemoteCodeSandbox implements CodeSandbox {

    private static final String EXECUTE_CODE_PATH = "/executeCode";

    @Value("${codesandbox.url}")
    private String codesandboxUrl;

    @Value("${codesandbox.timeout:60000}")
    private int timeout;

    @Resource
    private SandboxAuthSigner sandboxAuthSigner;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String url = codesandboxUrl + EXECUTE_CODE_PATH;
        String json = JSONUtil.toJsonStr(executeCodeRequest);
        Map<String, String> authHeaders = sandboxAuthSigner.buildAuthHeaders(json);

        // 使用 try-with-resources 确保 HTTP 连接资源被正确关闭
        try (HttpResponse response = HttpUtil.createPost(url)
                .addHeaders(authHeaders)
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
