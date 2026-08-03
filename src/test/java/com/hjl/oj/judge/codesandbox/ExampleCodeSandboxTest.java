package com.hjl.oj.judge.codesandbox;

import com.hjl.oj.judge.codesandbox.impl.ExampleCodeSandbox;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.hjl.oj.model.enums.ExecuteStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExampleCodeSandboxTest {

    @Test
    void successfulExecutionUsesExecuteStatus() {
        ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                .code("class Main {}")
                .language("java")
                .inputList(List.of("1"))
                .build();

        ExecuteCodeResponse response = new ExampleCodeSandbox().executeCode(request);

        assertEquals(ExecuteStatusEnum.ACCEPTED.getValue(), response.getStatus());
    }
}
