package org.imprime.ai.api.http.request.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload para atualizar ou remover a foto de perfil do usuário autenticado")
public record UpdateUserAvatarRequest(
        @Schema(description = "URL da foto de perfil obtida via `/media/upload?category=AVATAR` (envie `null` ou vazio para remover)")
        @Size(max = 1000, message = "A URL do avatar deve ter no máximo 1000 caracteres")
        String avatarUrl
) {
}
