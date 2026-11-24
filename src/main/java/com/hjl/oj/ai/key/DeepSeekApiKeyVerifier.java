package com.hjl.oj.ai.key;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 调用 DeepSeek 的模型列表接口（GET /models）校验 Key，不产生费用。
 */
@Slf4j
public class DeepSeekApiKeyVerifier implements ApiKeyVerifier {

    private final HttpClient httpClient;

    private final URI modelsUri;

    private final Duration timeout;

    public DeepSeekApiKeyVerifier(String baseUrl, Duration timeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.modelsUri = URI.create(baseUrl.replaceAll("/+$", "") + "/models");
        this.timeout = timeout;
    }

    @Override
    public void verify(String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(modelsUri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        int status;
        try {
            status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            log.warn("DeepSeek Key 校验请求失败: {}", e.toString());
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "暂时连不上 DeepSeek，无法校验 Key，请稍后再试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "校验 Key 时被中断，请重试");
        }
        if (status == 200) {
            return;
        }
        if (status == 401 || status == 403) {
            throw new BusinessException(ErrorCode.AI_KEY_INVALID);
        }
        if (status == 429) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }
        log.warn("DeepSeek Key 校验返回非预期状态码: {}", status);
        throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "DeepSeek 暂时无法校验 Key（HTTP " + status + "），请稍后再试");
    }
}
