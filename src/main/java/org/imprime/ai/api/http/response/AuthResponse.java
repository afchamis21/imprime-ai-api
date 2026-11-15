package org.imprime.ai.api.http.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.dto.TokenDTO;
import org.imprime.ai.api.model.dto.UserDTO;

@Schema(description = "Dados de autenticação contendo tokens JWT e informações básicas do usuário")
public record AuthResponse(
        @Schema(description = "Token JWT de acesso de curta duração (utilizado no header `Authorization: Bearer`)")
        TokenDTO accessToken,
        @Schema(description = "Token JWT de atualização de longa duração (utilizado em `/auth/refresh`)")
        TokenDTO refreshToken,
        @Schema(description = "Dados básicos do usuário autenticado")
        UserDTO user
) {
}
