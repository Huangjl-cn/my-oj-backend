package com.hjl.oj.judge.codesandbox;

import com.hjl.oj.judge.codesandbox.impl.RemoteCodeSandbox;
import com.hjl.oj.judge.codesandbox.model.ExecuteCaseRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.hjl.oj.model.enums.ExecuteStatusEnum;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * 调用真实沙箱的端到端集成测试，默认跳过。运行方式：
 * RUN_SANDBOX_TESTS=true CODESANDBOX_URL=http://<沙箱地址>:8100 mvn test -Dtest=CodeSandboxTest
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@EnabledIfEnvironmentVariable(named = "RUN_SANDBOX_TESTS", matches = "true")
class CodeSandboxTest {

    @Resource
    private RemoteCodeSandbox remoteCodeSandbox;

    @Test
    void executeCode() {
        String code = "public class Main {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.print(args.length + \":\" + args[0] + \":\" + args[1]);\n"
                + "    }\n"
                + "}\n";
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(QuestionSubmitLanguageEnum.JAVA.getValue())
                .cases(List.of(
                        ExecuteCaseRequest.builder().args(List.of("1", "2")).build(),
                        ExecuteCaseRequest.builder().args(List.of("3", "4")).build()))
                .build();
        ExecuteCodeResponse executeCodeResponse = remoteCodeSandbox.executeCode(executeCodeRequest);

        Assertions.assertEquals(ExecuteStatusEnum.ACCEPTED.getValue(), executeCodeResponse.getStatus());
        Assertions.assertEquals(List.of("2:1:2", "2:3:4"), executeCodeResponse.getOutputList());
    }
}
