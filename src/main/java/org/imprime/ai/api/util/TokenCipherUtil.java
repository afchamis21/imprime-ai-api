package org.imprime.ai.api.util;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class TokenCipherUtil {
    private static final String DEFAULT_SECRET = "imprime-ai-dev-crypto-key";
    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenCipherUtil() {
        this(DEFAULT_SECRET);
    }

    @Autowired
    public TokenCipherUtil(
            @Value("${imprime.ai.api.crypto.secret:imprime-ai-dev-crypto-key}") String secret) {
        this.keySpec = deriveKey(secret);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer combined = ByteBuffer.allocate(iv.length + cipherBytes.length);
            combined.put(iv);
            combined.put(cipherBytes);

            return PREFIX + Base64.getEncoder().encodeToString(combined.array());
        } catch (Exception e) {
            log.error("Failed to encrypt integration credential", e);
            throw new InternalErrorException("Failed to encrypt integration credential");
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        if (!encrypted.startsWith(PREFIX)) {
            return encrypted;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt integration credential", e);
            throw new InternalErrorException("Failed to decrypt integration credential");
        }
    }

    private SecretKeySpec deriveKey(String secret) {
        try {
            String effectiveSecret = (secret != null && !secret.isBlank()) ? secret : DEFAULT_SECRET;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(effectiveSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.error("Failed to derive AES key", e);
            throw new InternalErrorException("Failed to derive AES key");
        }
    }
}
