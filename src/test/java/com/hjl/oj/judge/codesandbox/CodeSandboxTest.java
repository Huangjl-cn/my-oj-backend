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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Scanner;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
public class CodeSandboxTest {
    @Value("${codesandbox.type:example}")
    private String type;

    @Resource
    private RemoteCodeSandbox remoteCodeSandbox;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            String type = scanner.next();
            CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
            String code = "int main() { }";
            String language = QuestionSubmitLanguageEnum.JAVA.getValue();
            ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                    .code(code)
                    .language(language)
                    .cases(testCases())
                    .build();
            ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        }
    }

    private static List<ExecuteCaseRequest> testCases() {
        return List.of(
                ExecuteCaseRequest.builder().args(List.of("1", "2")).build(),
                ExecuteCaseRequest.builder().args(List.of("3", "4")).build());
    }

    /**
     * 调用真实沙箱的集成测试，默认跳过。运行方式：
     * RUN_SANDBOX_TESTS=true CODESANDBOX_URL=http://<沙箱地址>:8100 mvn test -Dtest=CodeSandboxTest
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_SANDBOX_TESTS", matches = "true")
    void executeCode() {
        String code = "public class Main {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.print(args.length + \":\" + args[0] + \":\" + args[1]);\n"
                + "    }\n"
                + "}\n";
        String language = QuestionSubmitLanguageEnum.JAVA.getValue();
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .cases(testCases())
                .build();
        ExecuteCodeResponse executeCodeResponse = remoteCodeSandbox.executeCode(executeCodeRequest);

        Assertions.assertEquals(ExecuteStatusEnum.ACCEPTED.getValue(), executeCodeResponse.getStatus());
        Assertions.assertEquals(List.of("2:1:2", "2:3:4"), executeCodeResponse.getOutputList());
    }

    @Test
    void executeCodeByValue() {
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
        String code = "int main() { }";
        String language = QuestionSubmitLanguageEnum.JAVA.getValue();
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .cases(testCases())
                .build();
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        Assertions.assertNotNull(executeCodeResponse);
    }

    @Test
    void executeCodeByProxy() {
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
        codeSandbox = new CodeSandboxProxy(codeSandbox);
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int a = Integer.parseInt(args[0]);\n" +
                "        int b = Integer.parseInt(args[1]);\n" +
                "        System.out.println(a + b);\n" +
                "    }\n" +
                "}\n";
        String language = QuestionSubmitLanguageEnum.JAVA.getValue();
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .cases(testCases())
                .build();
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        Assertions.assertNotNull(executeCodeResponse);
    }
}
