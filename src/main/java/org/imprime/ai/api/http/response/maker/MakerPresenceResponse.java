package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Estado atual de presença online do estúdio Maker")
public record MakerPresenceResponse(
        @Schema(description = "Indica se o Maker está marcado como online", example = "true")
        boolean online,
        @Schema(description = "Instante UTC da última atividade registrada", example = "2026-11-23T22:15:00Z")
        Instant lastSeenAt,
        @Schema(description = "Tempo de expiração (TTL) da presença em segundos", example = "240")
        long ttlSeconds
) {
}
