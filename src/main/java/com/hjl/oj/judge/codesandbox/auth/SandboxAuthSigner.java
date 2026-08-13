package com.hjl.oj.judge.codesandbox.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代码沙箱调用鉴权签名器（ECDSA / secp256r1）。
 *
 * <p>后端作为唯一合法调用方持有私钥，沙箱只部署公钥（Java 8 原生支持验签，无需额外依赖）。
 * 签名的内容为规范化字符串 {@code timestamp \n nonce \n sha256Hex(body)}，
 * 配合沙箱侧的时间窗校验与 nonce 去重实现防重放，请求体本身保持明文传输。
 */
public final class SandboxAuthSigner {

    public static final String HEADER_KEY_ID = "X-Sandbox-Key-Id";
    public static final String HEADER_TIMESTAMP = "X-Sandbox-Timestamp";
    public static final String HEADER_NONCE = "X-Sandbox-Nonce";
    public static final String HEADER_SIGNATURE = "X-Sandbox-Signature";

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String KEY_ALGORITHM = "EC";
    private static final int NONCE_BYTES = 16;

    private final String keyId;
    private final PrivateKey privateKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SandboxAuthSigner(String keyId, PrivateKey privateKey) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey must not be null");
        }
        this.keyId = keyId;
        this.privateKey = privateKey;
    }

    /**
     * 从配置字符串加载私钥。支持两种格式：
     * <ul>
     *     <li>裸 Base64（PKCS#8 DER）</li>
     *     <li>带 {@code -----BEGIN PRIVATE KEY-----} 壳的 PEM 文本</li>
     * </ul>
     * 解析失败抛出 {@link IllegalStateException}，让应用启动即失败（fail-fast）。
     */
    public static SandboxAuthSigner fromEncodedKey(String keyId, String encodedKey) {
        try {
            String base64 = stripPemArmor(encodedKey).replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(base64);
            PrivateKey privateKey = KeyFactory.getInstance(KEY_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
            return new SandboxAuthSigner(keyId, privateKey);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "无法解析 codesandbox.auth.private-key：期望 EC P-256 的 PKCS#8 私钥（Base64 或 PEM 格式）", e);
        }
    }

    /**
     * 规范化字符串：沙箱侧必须用完全相同的格式重建，字段顺序与换行符都属于协议的一部分。
     */
    static String buildCanonicalString(long timestamp, String nonce, String bodyHash) {
        return timestamp + "\n" + nonce + "\n" + bodyHash;
    }

    /**
     * UTF-8 字节的 SHA-256 小写十六进制摘要，沙箱侧用同一实现重算请求体摘要。
     */
    public static String sha256Hex(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private static String stripPemArmor(String encodedKey) {
        String trimmed = encodedKey == null ? "" : encodedKey.trim();
        if (trimmed.startsWith("-----BEGIN")) {
            int headerEnd = trimmed.indexOf('\n');
            int footerStart = trimmed.indexOf("-----END");
            if (headerEnd < 0 || footerStart < 0) {
                throw new IllegalArgumentException("PEM 格式不完整");
            }
            return trimmed.substring(headerEnd + 1, footerStart);
        }
        return trimmed;
    }

    /**
     * 为一次沙箱调用构造鉴权请求头。返回固定顺序的四元组，供 {@code HttpRequest.addHeaders} 使用。
     */
    public Map<String, String> buildAuthHeaders(String requestBody) {
        long timestamp = System.currentTimeMillis();
        String nonce = newNonce();
        String bodyHash = sha256Hex(requestBody);
        String signature = sign(buildCanonicalString(timestamp, nonce, bodyHash));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_KEY_ID, keyId);
        headers.put(HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.put(HEADER_NONCE, nonce);
        headers.put(HEADER_SIGNATURE, signature);
        return Collections.unmodifiableMap(headers);
    }

    private String newNonce() {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }

    private String sign(String canonical) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("沙箱请求签名失败", e);
        }
    }
}
