package org.imprime.ai.api.notification.gateway;

/**
 * Resultado retornado pelo provedor após envio aceito.
 */
public record EmailSendResult(
        String provider,
        String messageId
) {
}
