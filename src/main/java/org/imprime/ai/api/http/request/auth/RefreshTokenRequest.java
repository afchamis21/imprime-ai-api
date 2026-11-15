package org.imprime.ai.api.http.request.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Requisição para renovação de token de acesso utilizando um Refresh Token")
public record RefreshTokenRequest(
        @Schema(description = "Refresh Token JWT válido e não revogado", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
        String token
) {
}
