package org.imprime.ai.api.http.request.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitação de redefinição de senha por e-mail")
public record ForgotPasswordRequest(
        @Schema(description = "Endereço de e-mail cadastrado", example = "cliente@imprimeai.shop", requiredMode = Schema.RequiredMode.REQUIRED)
        String email
) {
}
