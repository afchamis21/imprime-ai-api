package org.imprime.ai.api.http.request.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Confirmação de nova senha com token de uso único")
public record ResetPasswordRequest(
        @Schema(description = "Token recebido no e-mail de recuperação", requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
        @Schema(description = "Nova senha (8 a 32 caracteres com maiúscula, minúscula, número e símbolo)", requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword,
        @Schema(description = "Confirmação da nova senha", requiredMode = Schema.RequiredMode.REQUIRED)
        String confirmPassword
) {
}
