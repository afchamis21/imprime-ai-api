package org.imprime.ai.api.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configura a topologia RabbitMQ (Exchange principal, fila de e-mails, DLX e DLQ),
 * o conversor JSON baseado no Jackson 3 e o recoverer que encaminha falhas definitivas para a DLQ.
 */
@Configuration
public class RabbitMqNotificationConfig {

    @Bean
    public DirectExchange emailExchange(NotificationProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getExchange(), true, false);
    }

    @Bean
    public DirectExchange emailDeadLetterExchange(NotificationProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getDlx(), true, false);
    }

    @Bean
    public Queue emailQueue(NotificationProperties properties) {
        return QueueBuilder.durable(properties.getRabbitmq().getQueue())
                .withArgument("x-dead-letter-exchange", properties.getRabbitmq().getDlx())
                .withArgument("x-dead-letter-routing-key", properties.getRabbitmq().getDlqRoutingKey())
                .build();
    }

    @Bean
    public Queue emailDeadLetterQueue(NotificationProperties properties) {
        return QueueBuilder.durable(properties.getRabbitmq().getDlq()).build();
    }

    @Bean
    public Binding emailQueueBinding(
            Queue emailQueue,
            DirectExchange emailExchange,
            NotificationProperties properties) {
        return BindingBuilder.bind(emailQueue)
                .to(emailExchange)
                .with(properties.getRabbitmq().getRoutingKey());
    }

    @Bean
    public Binding emailDeadLetterBinding(
            Queue emailDeadLetterQueue,
            DirectExchange emailDeadLetterExchange,
            NotificationProperties properties) {
        return BindingBuilder.bind(emailDeadLetterQueue)
                .to(emailDeadLetterExchange)
                .with(properties.getRabbitmq().getDlqRoutingKey());
    }

    @Bean
    public MessageConverter emailRabbitMessageConverter(ObjectProvider<JsonMapper> jsonMapperProvider) {
        JsonMapper mapper = jsonMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
        return new JacksonJsonMessageConverter(
                mapper,
                "org.imprime.ai.api.notification.messaging",
                "org.imprime.ai.api.notification.event",
                "org.imprime.ai.api.slicing",
                "java.util",
                "java.math",
                "java.time"
        );
    }

    @Bean
    public MessageRecoverer emailMessageRecoverer() {
        return new RejectAndDontRequeueRecoverer();
    }
}
