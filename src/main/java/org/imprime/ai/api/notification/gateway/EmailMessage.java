package org.imprime.ai.api.notification.gateway;

/**
 * Payload pronto para despacho pelo provedor de e-mail ativo no ambiente.
 */
public record EmailMessage(
        String eventId,
        String to,
        String replyTo,
        String subject,
        String htmlBody,
        String textBody,
        String category
) {
}
