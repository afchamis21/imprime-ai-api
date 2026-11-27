package org.imprime.ai.api.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.gateway.EmailMessage;
import org.imprime.ai.api.notification.gateway.EmailSendResult;
import org.imprime.ai.api.notification.gateway.NonRetryableEmailException;
import org.imprime.ai.api.notification.gateway.RetryableEmailException;
import org.imprime.ai.api.notification.gateway.SmtpEmailGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailGatewayTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailGateway gateway;

    @BeforeEach
    void setUp() {
        NotificationProperties props = new NotificationProperties();
        gateway = new SmtpEmailGateway(mailSender, props);
    }

    @Test
    void sendsMimeMessageThroughJavaMailSender() {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);

        EmailSendResult result = gateway.send(new EmailMessage(
                "evt-1",
                "dest@imprimeai.shop",
                null,
                "Assunto",
                "<p>Olá</p>",
                "Olá",
                "TRANSACTIONAL_ORDER"
        ));

        assertEquals("SMTP_MAILPIT", result.provider());
        verify(mailSender).send(mime);
    }

    @Test
    void throwsNonRetryableWhenRecipientMissing() {
        assertThrows(NonRetryableEmailException.class, () -> gateway.send(
                new EmailMessage("evt-2", " ", null, "Assunto", "<p>x</p>", "x", "CAT")
        ));
    }

    @Test
    void throwsRetryableOnMailSendException() {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("connection refused")).when(mailSender).send(mime);

        assertThrows(RetryableEmailException.class, () -> gateway.send(
                new EmailMessage("evt-3", "dest@imprimeai.shop", null, "Assunto", "<p>x</p>", "x", "CAT")
        ));
    }
}
