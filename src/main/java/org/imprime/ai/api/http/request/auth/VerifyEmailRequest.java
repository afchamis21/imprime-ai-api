package org.imprime.ai.api.http.request.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitação de verificação de endereço de e-mail via token")
public record VerifyEmailRequest(
        @Schema(description = "Token enviado no e-mail de confirmação", requiredMode = Schema.RequiredMode.REQUIRED)
        String token
) {
}
