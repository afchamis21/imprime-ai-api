package org.imprime.ai.api.notification.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.notification.gateway.EmailGateway;
import org.imprime.ai.api.notification.gateway.EmailMessage;
import org.imprime.ai.api.notification.gateway.EmailSendResult;
import org.imprime.ai.api.notification.gateway.NonRetryableEmailException;
import org.imprime.ai.api.notification.gateway.RetryableEmailException;
import org.imprime.ai.api.notification.persistence.EmailNotificationLog;
import org.imprime.ai.api.notification.persistence.EmailNotificationLogRepository;
import org.imprime.ai.api.notification.persistence.EmailNotificationStatus;
import org.imprime.ai.api.notification.template.EmailTemplateRenderer;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Consumidor da fila principal de e-mails com garantia de idempotência, renderização Thymeleaf
 * e tratamento explícito entre falhas transitórias (retry + backoff) e permanentes (DLQ imediata).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailQueueConsumer {
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailNotificationLogRepository logRepository;
    private final EmailTemplateRenderer templateRenderer;
    private final EmailGateway emailGateway;

    @RabbitListener(queues = "${imprime.ai.notification.rabbitmq.queue:imprimeai.email.queue}")
    public void consume(EmailQueueMessage payload) {
        validatePayload(payload);

        EmailNotificationLog entry = resolveOrCreateLog(payload);
        if (EmailNotificationStatus.SENT.equals(entry.getStatus())) {
            log.info("E-mail já processado anteriormente (idempotência). Ignorando eventId={}", payload.eventId());
            return;
        }

        int attempt = (entry.getAttempts() != null ? entry.getAttempts() : 0) + 1;
        entry.setAttempts(attempt);
        entry.setStatus(EmailNotificationStatus.QUEUED);
        entry = logRepository.saveAndFlush(entry);

        try {
            EmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(
                    payload.templateName(),
                    payload.variables()
            );

            EmailSendResult result = emailGateway.send(new EmailMessage(
                    payload.eventId(),
                    payload.recipientEmail().trim(),
                    null,
                    payload.subject(),
                    rendered.htmlBody(),
                    rendered.textBody(),
                    payload.category() != null ? payload.category().name() : EmailCategory.TRANSACTIONAL_ORDER.name()
            ));

            entry.setStatus(EmailNotificationStatus.SENT);
            entry.setProvider(result != null ? result.provider() : null);
            entry.setProviderMessageId(result != null ? result.messageId() : null);
            entry.setLastError(null);
            entry.setSentAt(Instant.now());
            logRepository.save(entry);
        } catch (NonRetryableEmailException ex) {
            entry.setStatus(EmailNotificationStatus.DEAD_LETTERED);
            entry.setLastError(truncate(ex.getMessage(), 2000));
            logRepository.save(entry);
            log.error("Erro permanente ao enviar e-mail (eventId={}, to={}). Encaminhando para DLQ: {}",
                    payload.eventId(), payload.recipientEmail(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);
        } catch (RetryableEmailException ex) {
            entry.setStatus(EmailNotificationStatus.FAILED);
            entry.setLastError(truncate(ex.getMessage(), 2000));
            logRepository.save(entry);
            log.warn("Erro transitório na tentativa {} para e-mail (eventId={}, to={}): {}",
                    attempt, payload.eventId(), payload.recipientEmail(), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            entry.setStatus(EmailNotificationStatus.FAILED);
            entry.setLastError(truncate(ex.getMessage(), 2000));
            logRepository.save(entry);
            log.warn("Erro inesperado na tentativa {} para e-mail (eventId={}, to={}): {}",
                    attempt, payload.eventId(), payload.recipientEmail(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private void validatePayload(EmailQueueMessage payload) {
        if (payload == null) {
            throw new AmqpRejectAndDontRequeueException("Payload de e-mail nulo.");
        }
        if (isBlank(payload.eventId()) || isBlank(payload.templateName()) || isBlank(payload.subject())) {
            throw new AmqpRejectAndDontRequeueException("Payload de e-mail incompleto (eventId/template/subject).");
        }
        if (isBlank(payload.recipientEmail()) || !BASIC_EMAIL_PATTERN.matcher(payload.recipientEmail().trim()).matches()) {
            throw new AmqpRejectAndDontRequeueException("Endereço de e-mail destinatário inválido: " + payload.recipientEmail());
        }
    }

    private EmailNotificationLog resolveOrCreateLog(EmailQueueMessage payload) {
        return logRepository.findByEventId(payload.eventId()).orElseGet(() -> {
            EmailNotificationLog created = new EmailNotificationLog();
            created.setEventId(truncate(payload.eventId(), 128));
            created.setCategory(payload.category() != null ? payload.category().name() : EmailCategory.TRANSACTIONAL_ORDER.name());
            created.setTemplateName(truncate(payload.templateName(), 120));
            created.setRecipientEmail(truncate(payload.recipientEmail().trim(), 255));
            created.setSubject(truncate(payload.subject(), 255));
            created.setStatus(EmailNotificationStatus.QUEUED);
            created.setAttempts(0);
            try {
                return logRepository.saveAndFlush(created);
            } catch (DataIntegrityViolationException ex) {
                return logRepository.findByEventId(payload.eventId()).orElseThrow(() -> ex);
            }
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
