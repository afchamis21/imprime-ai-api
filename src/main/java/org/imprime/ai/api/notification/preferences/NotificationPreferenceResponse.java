package org.imprime.ai.api.notification.preferences;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Preferências de notificação por e-mail do usuário")
public record NotificationPreferenceResponse(
        @Schema(description = "Receber resumo de mensagens não lidas do chat")
        boolean chatDigestEnabled,
        @Schema(description = "Receber avisos de novas avaliações e respostas")
        boolean reviewsEnabled,
        @Schema(description = "Receber novidades e comunicações opcionais da plataforma")
        boolean marketingEnabled
) {
}
