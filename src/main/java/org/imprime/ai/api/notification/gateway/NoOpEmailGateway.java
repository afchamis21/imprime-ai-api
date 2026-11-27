package org.imprime.ai.api.notification.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback seguro para testes automatizados ou perfis sem provedor SMTP/Resend ativo.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(EmailGateway.class)
public class NoOpEmailGateway implements EmailGateway {

    @Override
    public EmailSendResult send(EmailMessage message) {
        log.debug("[NoOpEmailGateway] Simulando envio [{}] para [{}] assunto='{}'",
                message.category(), message.to(), message.subject());
        return new EmailSendResult("NOOP", message.eventId());
    }
}
