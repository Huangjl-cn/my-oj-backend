package com.hjl.oj.judge.codesandbox.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Year;
import java.util.Base64;

/**
 * 一次性密钥生成工具（手动运行 main 方法，不参与测试套件）。
 *
 * <p>用法：在 IDE 中直接运行本类，或执行
 * {@code java src/test/java/com/hjl/oj/judge/codesandbox/auth/SandboxAuthKeygenTool.java}。
 *
 * <p>输出：
 * <ul>
 *     <li>私钥 → 后端配置 {@code codesandbox.auth.private-key}（开发环境放 application-dev.yml，生产走环境变量）</li>
 *     <li>公钥 → 沙箱配置 {@code codesandbox.auth.public-key}</li>
 * </ul>
 */
public final class SandboxAuthKeygenTool {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();

        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        System.out.println("建议 key-id: sandbox-key-" + Year.now());
        System.out.println();
        System.out.println("私钥（后端 codesandbox.auth.private-key）:");
        System.out.println(privateKey);
        System.out.println();
        System.out.println("公钥（沙箱 codesandbox.auth.public-key）:");
        System.out.println(publicKey);
    }

    private SandboxAuthKeygenTool() {
    }
}
