package org.imprime.ai.api.http.request.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciais para autenticação do usuário")
public record LoginRequest(
        @Schema(description = "Endereço de e-mail cadastrado", example = "cliente@imprime.ai", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Senha do usuário", example = "Senha@123", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}
