package org.imprime.ai.api.slicing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.service.InstantCheckoutService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(name = "slicingRabbitListenerContainerFactory")
@RequiredArgsConstructor
public class SlicingValidationQueueConsumer {

    private final InstantCheckoutService instantCheckoutService;

    @RabbitListener(
            queues = "${imprime.ai.slicing.rabbitmq.queue:imprimeai.slicing.queue}",
            containerFactory = "slicingRabbitListenerContainerFactory"
    )
    public void onMessage(SlicingValidationQueueMessage message) {
        if (message == null || message.orderGuid() == null || message.orderGuid().isBlank()) {
            return;
        }
        log.info("Processing queued slicing validation for Order [{}] (attempt {})",
                message.orderGuid(), message.attempt());
        instantCheckoutService.processQueuedValidation(message.orderGuid());
    }
}
