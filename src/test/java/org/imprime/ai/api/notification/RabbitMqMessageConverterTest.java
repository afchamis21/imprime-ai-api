package org.imprime.ai.api.notification;

import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.config.RabbitMqNotificationConfig;
import org.imprime.ai.api.notification.messaging.EmailCategory;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RabbitMqMessageConverterTest {

    @Test
    void serializesAndDeserializesEmailQueueMessageWithJackson3() {
        RabbitMqNotificationConfig cfg = new RabbitMqNotificationConfig();
        @SuppressWarnings("unchecked")
        ObjectProvider<JsonMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(JsonMapper.builder().build());

        MessageConverter converter = cfg.emailRabbitMessageConverter(provider);

        EmailQueueMessage original = new EmailQueueMessage(
                "evt-json-1",
                EmailCategory.TRANSACTIONAL_ORDER,
                "order/purchase-confirmed-buyer",
                "lucas@imprimeai.shop",
                "Lucas",
                "Pedido confirmado",
                Map.of(
                        "totalFormatted", "R$ 120,00",
                        "rawAmount", new BigDecimal("120.00"),
                        "items", List.of(Map.of("name", "Suporte 3D", "qty", 2))
                ),
                Instant.parse("2026-11-27T12:00:00Z")
        );

        Message amqpMessage = converter.toMessage(original, new MessageProperties());
        Object decoded = converter.fromMessage(amqpMessage);

        assertInstanceOf(EmailQueueMessage.class, decoded);
        EmailQueueMessage result = (EmailQueueMessage) decoded;
        assertEquals("evt-json-1", result.eventId());
        assertEquals(EmailCategory.TRANSACTIONAL_ORDER, result.category());
        assertEquals("lucas@imprimeai.shop", result.recipientEmail());
        assertEquals("R$ 120,00", result.variables().get("totalFormatted"));
        assertNotNull(new NotificationProperties());
    }
}
