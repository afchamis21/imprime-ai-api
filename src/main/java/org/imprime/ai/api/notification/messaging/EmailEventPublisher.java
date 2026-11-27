package org.imprime.ai.api.notification.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publicador assíncrono para a exchange principal de e-mails no RabbitMQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final NotificationProperties properties;

    public void publish(EmailQueueMessage message) {
        if (message == null) {
            return;
        }
        if (!properties.isEnabled()) {
            log.debug("Notificações por e-mail desabilitadas; ignorando eventId={}", message.eventId());
            return;
        }
        if (message.recipientEmail() == null || message.recipientEmail().isBlank()) {
            log.warn("Ignorando publicação de e-mail sem destinatário (eventId={}, template={})",
                    message.eventId(), message.templateName());
            return;
        }

        try {
            rabbitTemplate.convertAndSend(
                    properties.getRabbitmq().getExchange(),
                    properties.getRabbitmq().getRoutingKey(),
                    message
            );
            log.debug("Evento de e-mail publicado na fila RabbitMQ (eventId={}, category={}, template={}, to={})",
                    message.eventId(), message.category(), message.templateName(), message.recipientEmail());
        } catch (AmqpException ex) {
            log.error("Falha ao publicar e-mail no RabbitMQ (eventId={}, template={}, to={}): {}",
                    message.eventId(), message.templateName(), message.recipientEmail(), ex.getMessage(), ex);
        }
    }
}
