package com.hjl.oj.judge.codesandbox.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 沙箱请求签名器测试：用公钥模拟沙箱侧的验签流程，
 * 验证签名闭环、篡改检测、nonce 唯一性与密钥加载的 fail-fast 行为。
 */
class SandboxAuthSignerTest {

    private static final String KEY_ID = "sandbox-key-test";
    private static final String BODY = "{\"language\":\"java\",\"code\":\"class Main {}\"}";

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** 模拟沙箱侧验签：用请求头重建规范化字符串，用公钥验签。 */
    private static boolean verifyLikeSandbox(Map<String, String> headers, String body, PublicKey publicKey)
            throws Exception {
        String canonical = headers.get(SandboxAuthSigner.HEADER_TIMESTAMP) + "\n"
                + headers.get(SandboxAuthSigner.HEADER_NONCE) + "\n"
                + SandboxAuthSigner.sha256Hex(body);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(canonical.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getDecoder().decode(headers.get(SandboxAuthSigner.HEADER_SIGNATURE)));
    }

    @Test
    void signedHeadersVerifyAgainstMatchingPublicKey() throws Exception {
        KeyPair keyPair = newKeyPair();
        SandboxAuthSigner signer = new SandboxAuthSigner(KEY_ID, keyPair.getPrivate());

        Map<String, String> headers = signer.buildAuthHeaders(BODY);

        assertEquals(KEY_ID, headers.get(SandboxAuthSigner.HEADER_KEY_ID));
        assertTrue(verifyLikeSandbox(headers, BODY, keyPair.getPublic()));
    }

    @Test
    void tamperedBodyFailsVerification() throws Exception {
        KeyPair keyPair = newKeyPair();
        SandboxAuthSigner signer = new SandboxAuthSigner(KEY_ID, keyPair.getPrivate());

        Map<String, String> headers = signer.buildAuthHeaders(BODY);

        assertFalse(verifyLikeSandbox(headers,
                "{\"language\":\"java\",\"code\":\"class Hacked {}\"}", keyPair.getPublic()));
    }

    @Test
    void everyRequestGetsFreshNonceAndSignature() throws Exception {
        SandboxAuthSigner signer = new SandboxAuthSigner(KEY_ID, newKeyPair().getPrivate());

        Map<String, String> first = signer.buildAuthHeaders(BODY);
        Map<String, String> second = signer.buildAuthHeaders(BODY);

        assertNotEquals(first.get(SandboxAuthSigner.HEADER_NONCE), second.get(SandboxAuthSigner.HEADER_NONCE));
        assertNotEquals(first.get(SandboxAuthSigner.HEADER_SIGNATURE), second.get(SandboxAuthSigner.HEADER_SIGNATURE));

        long timestamp = Long.parseLong(first.get(SandboxAuthSigner.HEADER_TIMESTAMP));
        assertTrue(Math.abs(System.currentTimeMillis() - timestamp) < 60_000,
                "时间戳应接近当前时间，实际相差 " + Math.abs(System.currentTimeMillis() - timestamp) + "ms");
    }

    @Test
    void loadsKeyFromRawBase64AndPemArmor() throws Exception {
        KeyPair keyPair = newKeyPair();
        String rawBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + rawBase64 + "\n-----END PRIVATE KEY-----";

        Map<String, String> fromRaw = SandboxAuthSigner.fromEncodedKey(KEY_ID, rawBase64).buildAuthHeaders(BODY);
        Map<String, String> fromPem = SandboxAuthSigner.fromEncodedKey(KEY_ID, pem).buildAuthHeaders(BODY);

        assertTrue(verifyLikeSandbox(fromRaw, BODY, keyPair.getPublic()));
        assertTrue(verifyLikeSandbox(fromPem, BODY, keyPair.getPublic()));
    }

    @Test
    void invalidKeyFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> SandboxAuthSigner.fromEncodedKey("kid", "not-a-valid-key"));
    }
}
