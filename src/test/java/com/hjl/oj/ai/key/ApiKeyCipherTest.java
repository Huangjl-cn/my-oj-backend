package com.hjl.oj.ai.key;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final ApiKeyCipher cipher = new ApiKeyCipher(KEY);

    @Test
    void encryptsAndDecryptsForSameUser() {
        String encrypted = cipher.encrypt("sk-secret-value", 7L);

        assertTrue(encrypted.startsWith("v1:"));
        assertFalse(encrypted.contains("sk-secret-value"));
        assertEquals("sk-secret-value", cipher.decrypt(encrypted, 7L));
    }

    @Test
    void usesRandomIvSoSamePlaintextDiffers() {
        assertNotEquals(cipher.encrypt("sk-secret-value", 7L), cipher.encrypt("sk-secret-value", 7L));
    }

    @Test
    void ciphertextMovedToAnotherUserCannotBeDecrypted() {
        String encrypted = cipher.encrypt("sk-secret-value", 7L);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted, 8L));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String encrypted = cipher.encrypt("sk-secret-value", 7L);
        byte[] payload = Base64.getDecoder().decode(encrypted.substring(3));
        payload[payload.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered, 7L));
    }

    @Test
    void differentMasterKeyCannotDecrypt() {
        String encrypted = cipher.encrypt("sk-secret-value", 7L);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        ApiKeyCipher other = new ApiKeyCipher(Base64.getEncoder().encodeToString(otherKey));

        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted, 7L));
    }

    @Test
    void missingOrInvalidMasterKeyFailsFast() {
        assertThrows(IllegalStateException.class, () -> new ApiKeyCipher(null));
        assertThrows(IllegalStateException.class, () -> new ApiKeyCipher(" "));
        assertThrows(IllegalStateException.class, () -> new ApiKeyCipher("not-base64!!"));
        assertThrows(IllegalStateException.class,
                () -> new ApiKeyCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
