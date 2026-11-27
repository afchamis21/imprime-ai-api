package org.imprime.ai.api.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Propriedades centrais do módulo de notificações e disparo de e-mails transacionais.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "imprime.ai.notification")
public class NotificationProperties {
    private boolean enabled = true;
    private String publicBaseUrl = "https://imprimeai.shop";
    private Mail mail = new Mail();
    private RabbitMq rabbitmq = new RabbitMq();
    private Chat chat = new Chat();
    private Security security = new Security();

    @Data
    public static class Mail {
        private String provider = "smtp";
        private String fromAddress = "nao-responda@imprimeai.shop";
        private String fromName = "Imprime AÍ";
        private String replyTo = "suporte@imprimeai.shop";
        private String resendApiKey = "";
    }

    @Data
    public static class RabbitMq {
        private String exchange = "imprimeai.email.exchange";
        private String queue = "imprimeai.email.queue";
        private String routingKey = "imprimeai.email.send";
        private String dlx = "imprimeai.email.dlx";
        private String dlq = "imprimeai.email.dlq";
        private String dlqRoutingKey = "imprimeai.email.dlq";
    }

    @Data
    public static class Chat {
        private Duration debounceWindow = Duration.ofMinutes(5);
        private Duration offerDebounceWindow = Duration.ofMinutes(2);
        private Duration cooldownWindow = Duration.ofMinutes(15);
        private Duration activeViewTtl = Duration.ofSeconds(90);
        private Duration maxActiveDeferral = Duration.ofMinutes(15);
        private long pollInterval = 30_000L;
        private int batchSize = 50;
    }

    @Data
    public static class Security {
        private Duration passwordResetTtl = Duration.ofMinutes(30);
        private Duration emailVerificationTtl = Duration.ofHours(24);
    }

    public String normalizedBaseUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return "https://imprimeai.shop";
        }
        String trimmed = publicBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
