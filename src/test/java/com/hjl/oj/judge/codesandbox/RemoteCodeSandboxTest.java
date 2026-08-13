package com.hjl.oj.judge.codesandbox;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.auth.SandboxAuthSigner;
import com.hjl.oj.judge.codesandbox.impl.RemoteCodeSandbox;
import com.hjl.oj.judge.codesandbox.model.ExecuteCaseRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RemoteCodeSandboxTest {

    private static SandboxAuthSigner newTestSigner() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return new SandboxAuthSigner("sandbox-key-test", generator.generateKeyPair().getPrivate());
    }

    @Test
    void nonJavaLanguageUsesTheUnifiedExecuteCodeEndpoint() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverExecutor);
        server.createContext("/executeCode", exchange -> {
            requestCount.incrementAndGet();
            authHeader.set(exchange.getRequestHeaders().getFirst(SandboxAuthSigner.HEADER_KEY_ID));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"outputList\":[\"1\"],\"message\":\"ok\",\"status\":1,"
                    + "\"judgeInfo\":{\"message\":\"Accepted\",\"memory\":1,\"time\":1}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            RemoteCodeSandbox codeSandbox = new RemoteCodeSandbox();
            ReflectionTestUtils.setField(codeSandbox, "codesandboxUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(codeSandbox, "timeout", 1_000);
            ReflectionTestUtils.setField(codeSandbox, "sandboxAuthSigner", newTestSigner());
            ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                    .code("print(input())")
                    .language(QuestionSubmitLanguageEnum.PYTHON.getValue())
                    .cases(List.of(ExecuteCaseRequest.builder().args(List.of("1")).build()))
                    .build();

            ExecuteCodeResponse response = codeSandbox.executeCode(request);

            assertEquals(List.of("1"), response.getOutputList());
            assertEquals(1, requestCount.get());
            assertEquals("sandbox-key-test", authHeader.get());
            assertTrue(requestBody.get().contains("\"cases\":[{\"args\":[\"1\"]}]"));
            assertFalse(requestBody.get().contains("inputList"));
        } finally {
            server.stop(0);
            serverExecutor.close();
        }
    }

    @Test
    void executeCodeThrowsWhenSandboxTimesOut() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverExecutor);
        server.createContext("/executeCode", exchange -> {
            try {
                Thread.sleep(500);
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (Exception ignored) {
                // The client closes the connection after its read timeout.
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            RemoteCodeSandbox codeSandbox = new RemoteCodeSandbox();
            ReflectionTestUtils.setField(codeSandbox, "codesandboxUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(codeSandbox, "timeout", 100);
            ReflectionTestUtils.setField(codeSandbox, "sandboxAuthSigner", newTestSigner());
            ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                    .code("class Main {}")
                    .language(QuestionSubmitLanguageEnum.JAVA.getValue())
                    .cases(List.of(ExecuteCaseRequest.builder().args(List.of("1")).build()))
                    .build();

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> codeSandbox.executeCode(request));

            assertEquals(ErrorCode.API_REQUEST_ERROR.getCode(), exception.getCode());
        } finally {
            server.stop(0);
            serverExecutor.close();
        }
    }
}
