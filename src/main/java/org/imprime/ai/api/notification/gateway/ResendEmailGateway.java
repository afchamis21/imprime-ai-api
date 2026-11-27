package org.imprime.ai.api.notification.gateway;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gateway de produção usando o SDK oficial da Resend para Java com Idempotency-Key.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "imprime.ai.notification.mail", name = "provider", havingValue = "resend")
public class ResendEmailGateway implements EmailGateway {
    private static final String PROVIDER = "RESEND";

    private final NotificationProperties properties;
    private final Resend customClient;

    @Autowired
    public ResendEmailGateway(NotificationProperties properties) {
        this(properties, null);
    }

    public ResendEmailGateway(NotificationProperties properties, Resend customClient) {
        this.properties = properties;
        this.customClient = customClient;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        if (message == null || message.to() == null || message.to().isBlank()) {
            throw new NonRetryableEmailException("Destinatário de e-mail ausente.");
        }

        Resend client = resolveClient();
        String fromHeader = formatSender(properties.getMail().getFromName(), properties.getMail().getFromAddress());

        CreateEmailOptions.Builder optionsBuilder = CreateEmailOptions.builder()
                .from(fromHeader)
                .to(message.to().trim())
                .subject(message.subject())
                .html(message.htmlBody());

        if (message.textBody() != null && !message.textBody().isBlank()) {
            optionsBuilder.text(message.textBody());
        }

        String replyTo = (message.replyTo() != null && !message.replyTo().isBlank())
                ? message.replyTo().trim()
                : properties.getMail().getReplyTo();
        if (replyTo != null && !replyTo.isBlank()) {
            optionsBuilder.replyTo(replyTo);
        }

        RequestOptions.Builder reqBuilder = RequestOptions.builder();
        if (message.eventId() != null && !message.eventId().isBlank()) {
            reqBuilder.setIdempotencyKey(message.eventId());
        }

        try {
            CreateEmailResponse response = client.emails().send(optionsBuilder.build(), reqBuilder.build());
            String id = response != null ? response.getId() : null;
            log.info("E-mail [{}] enviado via Resend para [{}] (id={}, eventId={})",
                    message.category(), message.to(), id, message.eventId());
            return new EmailSendResult(PROVIDER, id);
        } catch (ResendException ex) {
            Integer status = ex.getStatusCode();
            if (status != null && status >= 400 && status < 500 && status != 429 && status != 408) {
                throw new NonRetryableEmailException(
                        "Resend rejeitou permanentemente o e-mail (HTTP " + status + "): " + ex.getMessage(), ex);
            }
            throw new RetryableEmailException(
                    "Falha transitória ao enviar e-mail via Resend (HTTP " + status + "): " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new RetryableEmailException("Erro inesperado de rede/cliente ao chamar Resend: " + ex.getMessage(), ex);
        }
    }

    private Resend resolveClient() {
        if (customClient != null) {
            return customClient;
        }
        String apiKey = properties.getMail().getResendApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new NonRetryableEmailException("RESEND_API_KEY não configurada no ambiente de produção.");
        }
        return new Resend(apiKey.trim());
    }

    private String formatSender(String fromName, String fromAddress) {
        if (fromName == null || fromName.isBlank()) {
            return fromAddress;
        }
        return fromName.trim() + " <" + fromAddress.trim() + ">";
    }
}
