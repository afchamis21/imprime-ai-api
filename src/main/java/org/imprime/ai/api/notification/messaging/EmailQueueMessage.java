package org.imprime.ai.api.notification.messaging;

import java.time.Instant;
import java.util.Map;

/**
 * Evento leve trafegado na fila RabbitMQ para disparo assíncrono de e-mail transacional.
 */
public record EmailQueueMessage(
        String eventId,
        EmailCategory category,
        String templateName,
        String recipientEmail,
        String recipientName,
        String subject,
        Map<String, Object> variables,
        Instant occurredAt
) {
}
