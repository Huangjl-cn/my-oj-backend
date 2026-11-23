package com.hjl.oj.judge.codesandbox;

import com.hjl.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 代理模式：可以无侵入地完成功能拓展
 */
@Slf4j
public class CodeSandboxProxy implements CodeSandbox {

    private final CodeSandbox codeSandbox;

    public CodeSandboxProxy(CodeSandbox codeSandbox) {
        this.codeSandbox = codeSandbox;
    }

    /**
     * 请求和响应含用户代码与全部用例输入输出，只在 DEBUG 级别输出（dev 默认开启，prod 不输出），
     * 避免生产日志膨胀和测试用例外泄。
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        log.debug("代码沙箱请求信息：{}", executeCodeRequest);
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        log.debug("代码沙箱响应信息：{}", executeCodeResponse);
        return executeCodeResponse;
    }
}
