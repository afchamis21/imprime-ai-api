package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.AuthToken;

import java.time.Instant;

@Schema(description = "Dados de um token JWT emitido e sua data de expiração")
public record TokenDTO(
        @Schema(description = "Token JWT assinado", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String token,
        @Schema(description = "Data e hora de expiração em UTC (ISO-8601)", example = "2026-11-15T01:35:00Z")
        Instant expiresAt
) {
    public static TokenDTO from(AuthToken token) {
        return new TokenDTO(token.getToken(), token.getExpiresAt());
    }
}
