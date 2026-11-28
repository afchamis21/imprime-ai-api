package org.imprime.ai.api.slicing;

import org.imprime.ai.api.config.SlicingValidationProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(ConnectionFactory.class)
public class RabbitMqSlicingConfig {

    @Bean
    public DirectExchange slicingExchange(SlicingValidationProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getExchange(), true, false);
    }

    @Bean
    public DirectExchange slicingDeadLetterExchange(SlicingValidationProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getDlx(), true, false);
    }

    @Bean
    public Queue slicingQueue(SlicingValidationProperties properties) {
        return QueueBuilder.durable(properties.getRabbitmq().getQueue())
                .withArgument("x-dead-letter-exchange", properties.getRabbitmq().getDlx())
                .withArgument("x-dead-letter-routing-key", properties.getRabbitmq().getDlqRoutingKey())
                .build();
    }

    @Bean
    public Queue slicingDeadLetterQueue(SlicingValidationProperties properties) {
        return QueueBuilder.durable(properties.getRabbitmq().getDlq()).build();
    }

    @Bean
    public Binding slicingQueueBinding(
            Queue slicingQueue,
            DirectExchange slicingExchange,
            SlicingValidationProperties properties
    ) {
        return BindingBuilder.bind(slicingQueue)
                .to(slicingExchange)
                .with(properties.getRabbitmq().getRoutingKey());
    }

    @Bean
    public Binding slicingDeadLetterBinding(
            Queue slicingDeadLetterQueue,
            DirectExchange slicingDeadLetterExchange,
            SlicingValidationProperties properties
    ) {
        return BindingBuilder.bind(slicingDeadLetterQueue)
                .to(slicingDeadLetterExchange)
                .with(properties.getRabbitmq().getDlqRoutingKey());
    }

    @Bean(name = "slicingRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory slicingRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter emailRabbitMessageConverter,
            SlicingValidationProperties properties
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(emailRabbitMessageConverter);
        factory.setPrefetchCount(1);
        int concurrency = Math.max(1, properties.getMaxConcurrentJobs());
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
