package org.imprime.ai.api.notification.preferences;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Atualização parcial das preferências de notificação por e-mail")
public record UpdateNotificationPreferencesRequest(
        Boolean chatDigestEnabled,
        Boolean reviewsEnabled,
        Boolean marketingEnabled
) {
}
