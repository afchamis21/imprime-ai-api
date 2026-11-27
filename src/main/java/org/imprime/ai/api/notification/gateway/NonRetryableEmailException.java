package org.imprime.ai.api.notification.gateway;

/**
 * Falha permanente de validação/configuração que deve ser encaminhada imediatamente para a DLQ.
 */
public class NonRetryableEmailException extends RuntimeException {
    public NonRetryableEmailException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonRetryableEmailException(String message) {
        super(message);
    }
}
