package org.imprime.ai.api.model.dto;

import org.imprime.ai.api.model.AuthToken;

import java.time.Instant;

public record TokenDTO(
        String token,
        Instant expiresAt
) {
    public static TokenDTO from(AuthToken token) {
        return new TokenDTO(token.getToken(), token.getExpiresAt());
    }
}
