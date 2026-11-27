package org.imprime.ai.api.notification.gateway;

/**
 * Falha transitória de comunicação/provedor elegível para retry e backoff no RabbitMQ.
 */
public class RetryableEmailException extends RuntimeException {
    public RetryableEmailException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryableEmailException(String message) {
        super(message);
    }
}
