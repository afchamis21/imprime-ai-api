package org.imprime.ai.api.notification.gateway;

/**
 * Contrato desacoplado de envio de e-mails transacionais.
 */
public interface EmailGateway {
    EmailSendResult send(EmailMessage message);
}
