package org.imprime.ai.api.util;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.exception.InternalErrorException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
public class HashUtil {

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();
        } catch (Exception e) {
            log.error("Error while generating SHA-256", e);
            throw new InternalErrorException("Unexpected error doing SHA-256 hash");
        }
    }
}