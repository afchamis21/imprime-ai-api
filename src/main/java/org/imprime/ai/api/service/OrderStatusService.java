package org.imprime.ai.api.service;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.notification.event.OrderStatusChangedNotificationEvent;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class OrderStatusService {
    private static final Set<OrderStatusCd> NOTIFIABLE_STATUSES = Set.of(
            OrderStatusCd.IN_PRODUCTION,
            OrderStatusCd.READY_TO_SHIP,
            OrderStatusCd.IN_TRANSIT,
            OrderStatusCd.COMPLETED,
            OrderStatusCd.CANCELLED
    );

    private final OrderStatusRepository orderStatusRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderStatusService(OrderStatusRepository orderStatusRepository) {
        this(orderStatusRepository, null);
    }

    @Autowired
    public OrderStatusService(OrderStatusRepository orderStatusRepository, ApplicationEventPublisher eventPublisher) {
        this.orderStatusRepository = orderStatusRepository;
        this.eventPublisher = eventPublisher;
    }

    private Set<OrderStatusCd> possibleStatusesToMove(OrderStatusCd from) {
        if (from == null) {
            return Set.of(OrderStatusCd.CREATED);
        }

        return switch (from) {
            case CREATED -> Set.of(OrderStatusCd.QUOTING, OrderStatusCd.CONFIRMED, OrderStatusCd.CANCELLED);
            case QUOTING -> Set.of(OrderStatusCd.CONFIRMED, OrderStatusCd.CANCELLED);
            case CONFIRMED -> Set.of(OrderStatusCd.IN_PRODUCTION, OrderStatusCd.COMPLETED, OrderStatusCd.CANCELLED, OrderStatusCd.REFUNDED);
            case IN_PRODUCTION -> Set.of(OrderStatusCd.READY_TO_SHIP, OrderStatusCd.CANCELLED, OrderStatusCd.REFUNDED);
            case READY_TO_SHIP -> Set.of(OrderStatusCd.IN_PRODUCTION, OrderStatusCd.IN_TRANSIT, OrderStatusCd.CANCELLED, OrderStatusCd.REFUNDED);
            case IN_TRANSIT -> Set.of(OrderStatusCd.COMPLETED, OrderStatusCd.CANCELLED, OrderStatusCd.REFUNDED);
            case COMPLETED -> Set.of(OrderStatusCd.CANCELLED, OrderStatusCd.REFUNDED);
            case CANCELLED -> Set.of(OrderStatusCd.REFUNDED);
            case ERROR, REFUNDED -> Set.of();
        };
    }

    public boolean canMoveTo(OrderStatusCd from, OrderStatusCd to) {
        return possibleStatusesToMove(from).contains(to);
    }

    public OrderStatus move(@NonNull Order order, @NonNull OrderStatusCd to) {
        return move(order, to, true);
    }

    public OrderStatus move(@NonNull Order order, @NonNull OrderStatusCd to, boolean publishNotification) {
        Optional<OrderStatus> latest = orderStatusRepository.findLatestStatusByOrderId(order.getId());
        OrderStatusCd from = latest.map(OrderStatus::getStatusCd).orElse(null);

        if (!canMoveTo(from, to)) {
            log.error("Can't move order [{}] status from {} to {}", order.getId(), from, to);
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        OrderStatus orderStatus = new OrderStatus();
        orderStatus.setOrderId(order.getId());
        orderStatus.setStatusDate(Instant.now());
        orderStatus.setStatusCd(to);

        OrderStatus saved = orderStatusRepository.save(orderStatus);
        if (publishNotification && eventPublisher != null && NOTIFIABLE_STATUSES.contains(to)) {
            eventPublisher.publishEvent(new OrderStatusChangedNotificationEvent(
                    order.getId(),
                    order.getGuid(),
                    from,
                    to,
                    order.getCancellationReason(),
                    saved != null ? saved.getId() : null
            ));
        }
        return saved;
    }

    public List<OrderStatus> findHistory(@NonNull Order order) {
        return orderStatusRepository.findAllByOrderIdOrderByStatusDateAsc(order.getId());
    }

    public @NonNull OrderStatus findLatest(@NonNull Order order) {
        Optional<OrderStatus> latest = orderStatusRepository.findLatestStatusByOrderId(order.getId());
        if (latest.isEmpty()) {
            log.error("Can't find latest order status for order {}", order.getId());
            throw new InternalErrorException();
        }

        return latest.get();
    }
}
