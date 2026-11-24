package com.hjl.oj.ai.key;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 用户 API Key 的加解密（AES-256-GCM）。
 *
 * <p>密文格式：{@code v1:} + Base64(12 字节随机 IV + 密文与认证标签)。用户 ID 作为附加认证数据参与加密，
 * 把一条密文挪到另一个用户名下会解密失败。主密钥只来自配置，不进数据库。
 */
public final class ApiKeyCipher {

    private static final String VERSION_PREFIX = "v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int KEY_BYTES = 32;

    private static final int IV_BYTES = 12;

    private static final int TAG_BITS = 128;

    private final SecretKey key;

    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "缺少 ai.key-encryption-key：生产环境请通过环境变量 AI_KEY_ENCRYPTION_KEY 注入（openssl rand -base64 32 生成）");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ai.key-encryption-key 不是合法的 Base64", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("ai.key-encryption-key 必须是 32 字节（Base64 编码后 44 个字符）");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext, long userId) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData(userId));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    public String decrypt(String ciphertext, long userId) {
        if (ciphertext == null || !ciphertext.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("不支持的 API Key 密文格式");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalStateException("API Key 密文已损坏");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            cipher.updateAAD(associatedData(userId));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("API Key 解密失败：主密钥已更换或密文被改动", e);
        }
    }

    private static byte[] associatedData(long userId) {
        return ("user:" + userId).getBytes(StandardCharsets.UTF_8);
    }
}
