package org.imprime.ai.api.notification;

import org.imprime.ai.api.notification.gateway.EmailGateway;
import org.imprime.ai.api.notification.gateway.EmailMessage;
import org.imprime.ai.api.notification.gateway.EmailSendResult;
import org.imprime.ai.api.notification.gateway.NonRetryableEmailException;
import org.imprime.ai.api.notification.gateway.RetryableEmailException;
import org.imprime.ai.api.notification.messaging.EmailCategory;
import org.imprime.ai.api.notification.messaging.EmailQueueConsumer;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.imprime.ai.api.notification.persistence.EmailNotificationLog;
import org.imprime.ai.api.notification.persistence.EmailNotificationLogRepository;
import org.imprime.ai.api.notification.persistence.EmailNotificationStatus;
import org.imprime.ai.api.notification.template.EmailTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailQueueConsumerTest {

    @Mock
    private EmailNotificationLogRepository logRepository;
    @Mock
    private EmailTemplateRenderer templateRenderer;
    @Mock
    private EmailGateway emailGateway;

    private EmailQueueConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EmailQueueConsumer(logRepository, templateRenderer, emailGateway);
    }

    @Test
    void consumeRendersTemplateSendsEmailAndMarksSent() {
        EmailQueueMessage msg = sampleMessage("evt-1", "buyer@imprimeai.shop");
        when(logRepository.findByEventId("evt-1")).thenReturn(Optional.empty());
        when(logRepository.saveAndFlush(any(EmailNotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepository.save(any(EmailNotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRenderer.render("order/purchase-confirmed-buyer", msg.variables()))
                .thenReturn(new EmailTemplateRenderer.RenderedEmail("<h1>Olá</h1>", "Olá"));
        when(emailGateway.send(any(EmailMessage.class)))
                .thenReturn(new EmailSendResult("SMTP_MAILPIT", "<msg-1@localhost>"));

        consumer.consume(msg);

        ArgumentCaptor<EmailNotificationLog> captor = ArgumentCaptor.forClass(EmailNotificationLog.class);
        verify(logRepository).save(captor.capture());
        EmailNotificationLog saved = captor.getValue();
        assertEquals(EmailNotificationStatus.SENT, saved.getStatus());
        assertEquals("SMTP_MAILPIT", saved.getProvider());
        assertEquals("<msg-1@localhost>", saved.getProviderMessageId());
        assertEquals(1, saved.getAttempts());
        assertNotNull(saved.getSentAt());
    }

    @Test
    void consumeSkipsWhenEventAlreadySent() {
        EmailQueueMessage msg = sampleMessage("evt-dup", "buyer@imprimeai.shop");
        EmailNotificationLog existing = new EmailNotificationLog();
        existing.setEventId("evt-dup");
        existing.setStatus(EmailNotificationStatus.SENT);
        when(logRepository.findByEventId("evt-dup")).thenReturn(Optional.of(existing));

        consumer.consume(msg);

        verifyNoInteractions(templateRenderer, emailGateway);
        verify(logRepository, never()).saveAndFlush(any());
    }

    @Test
    void consumeRejectsImmediatelyToDlqOnInvalidRecipient() {
        EmailQueueMessage msg = sampleMessage("evt-bad-mail", "email-invalido");

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.consume(msg));
        verifyNoInteractions(templateRenderer, emailGateway);
    }

    @Test
    void consumeMarksDeadLetteredAndRejectsWithoutRequeueOnNonRetryableError() {
        EmailQueueMessage msg = sampleMessage("evt-perm", "buyer@imprimeai.shop");
        when(logRepository.findByEventId("evt-perm")).thenReturn(Optional.empty());
        when(logRepository.saveAndFlush(any(EmailNotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepository.save(any(EmailNotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRenderer.render(any(), any()))
                .thenReturn(new EmailTemplateRenderer.RenderedEmail("<p>Oi</p>", "Oi"));
        when(emailGateway.send(any()))
                .thenThrow(new NonRetryableEmailException("422 Invalid email"));

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.consume(msg));

        ArgumentCaptor<EmailNotificationLog> captor = ArgumentCaptor.forClass(EmailNotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertEquals(EmailNotificationStatus.DEAD_LETTERED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getLastError().contains("422"));
    }

    @Test
    void consumeMarksFailedAndRethrowsOnRetryableError() {
        EmailQueueMessage msg = sampleMessage("evt-retry", "buyer@imprimeai.shop");
        when(logRepository.findByEventId("evt-retry")).thenReturn(Optional.empty());
        when(logRepository.saveAndFlush(any(EmailNotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepository.save(any(EmailNotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRenderer.render(any(), any()))
                .thenReturn(new EmailTemplateRenderer.RenderedEmail("<p>Oi</p>", "Oi"));
        when(emailGateway.send(any()))
                .thenThrow(new RetryableEmailException("SMTP timeout"));

        assertThrows(RetryableEmailException.class, () -> consumer.consume(msg));

        ArgumentCaptor<EmailNotificationLog> captor = ArgumentCaptor.forClass(EmailNotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertEquals(EmailNotificationStatus.FAILED, captor.getValue().getStatus());
        assertEquals("SMTP timeout", captor.getValue().getLastError());
    }

    private EmailQueueMessage sampleMessage(String eventId, String email) {
        return new EmailQueueMessage(
                eventId,
                EmailCategory.TRANSACTIONAL_ORDER,
                "order/purchase-confirmed-buyer",
                email,
                "Lucas",
                "Pedido confirmado!",
                Map.of("buyerName", "Lucas"),
                Instant.now()
        );
    }
}
