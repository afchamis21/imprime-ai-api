package org.imprime.ai.api.notification.gateway;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Gateway SMTP para desenvolvimento local com Mailpit (localhost:1025, sem autenticação).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "imprime.ai.notification.mail", name = "provider", havingValue = "smtp")
@RequiredArgsConstructor
public class SmtpEmailGateway implements EmailGateway {
    private static final String PROVIDER = "SMTP_MAILPIT";

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    @Override
    public EmailSendResult send(EmailMessage message) {
        if (message == null || message.to() == null || message.to().isBlank()) {
            throw new NonRetryableEmailException("Destinatário de e-mail ausente.");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            String fromAddress = properties.getMail().getFromAddress();
            String fromName = properties.getMail().getFromName();
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(message.to().trim());

            String replyTo = (message.replyTo() != null && !message.replyTo().isBlank())
                    ? message.replyTo().trim()
                    : properties.getMail().getReplyTo();
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }

            helper.setSubject(message.subject());
            String textFallback = message.textBody() != null ? message.textBody() : "";
            String htmlBody = message.htmlBody() != null ? message.htmlBody() : textFallback;
            helper.setText(textFallback, htmlBody);

            if (message.eventId() != null && !message.eventId().isBlank()) {
                mimeMessage.setHeader("X-ImprimeAI-Event-Id", message.eventId());
            }

            mailSender.send(mimeMessage);
            String messageId = mimeMessage.getMessageID();
            log.info("E-mail [{}] enviado via Mailpit/SMTP para [{}] (eventId={})",
                    message.category(), message.to(), message.eventId());
            return new EmailSendResult(PROVIDER, messageId);
        } catch (AddressException | MailParseException | UnsupportedEncodingException ex) {
            throw new NonRetryableEmailException("E-mail inválido para envio SMTP: " + ex.getMessage(), ex);
        } catch (MailAuthenticationException ex) {
            throw new NonRetryableEmailException("Falha de autenticação SMTP: " + ex.getMessage(), ex);
        } catch (MessagingException | MailException ex) {
            throw new RetryableEmailException("Falha transitória ao enviar e-mail via SMTP/Mailpit: " + ex.getMessage(), ex);
        }
    }
}
