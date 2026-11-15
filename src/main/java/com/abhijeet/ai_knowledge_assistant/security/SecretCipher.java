package com.abhijeet.ai_knowledge_assistant.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for secrets at rest (currently user-supplied API keys).
 *
 * The key comes from APP_ENCRYPTION_KEY (base64, 32 bytes). If that is absent a
 * random key is generated per process, so stored keys still work but are lost on
 * restart - the log warns about it rather than silently storing something that
 * will not decrypt later.
 *
 * GCM is used rather than CBC because it authenticates the ciphertext: a tampered
 * value fails to decrypt instead of producing garbage.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final boolean persistent;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.encryption-key:}") String configured) {
        if (configured != null && !configured.isBlank()) {
            byte[] raw = Base64.getDecoder().decode(configured.trim());
            if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
                throw new IllegalStateException(
                        "APP_ENCRYPTION_KEY must decode to 16, 24 or 32 bytes");
            }
            this.key = new SecretKeySpec(raw, "AES");
            this.persistent = true;
            log.info("BYOK encryption key loaded from configuration");
        } else {
            try {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(256);
                this.key = generator.generateKey();
            } catch (Exception e) {
                throw new IllegalStateException("Could not initialise encryption key", e);
            }
            this.persistent = false;
            log.warn("APP_ENCRYPTION_KEY is not set: user API keys will be encrypted with a "
                    + "per-process key and will not survive a restart. Set APP_ENCRYPTION_KEY "
                    + "(32 random bytes, base64) for persistence.");
        }
    }

    public boolean isPersistent() {
        return persistent;
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt stored value", e);
        }
    }
}
