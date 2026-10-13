package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.repo.db.OrderStatusLkupRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusService {
    private final OrderStatusLkupRepository orderStatusLkupRepository;
    private final OrderStatusRepository orderStatusRepository;

    private Set<OrderStatusCd> possibleStatusesToMove(OrderStatusCd from) {
        if (from == null) {
            return Set.of(OrderStatusCd.CREATED);
        }

        return switch (from) {
            case CREATED -> Set.of(OrderStatusCd.QUOTING, OrderStatusCd.CANCELLED);
            case QUOTING -> Set.of(OrderStatusCd.CONFIRMED, OrderStatusCd.CANCELLED);
            case CONFIRMED -> Set.of(OrderStatusCd.IN_PRODUCTION, OrderStatusCd.CANCELLED);
            case IN_PRODUCTION -> Set.of(OrderStatusCd.IN_TRANSIT, OrderStatusCd.CANCELLED);
            case IN_TRANSIT -> Set.of(OrderStatusCd.COMPLETED, OrderStatusCd.CANCELLED);
            case COMPLETED -> Set.of(OrderStatusCd.CANCELLED);
            case CANCELLED, ERROR -> Set.of();
        };
    }

    private boolean canMoveTo(OrderStatusCd from, OrderStatusCd to) {
        return  possibleStatusesToMove(from).contains(to);
    }

    public OrderStatus move(@NonNull Order order, @NonNull OrderStatusCd to) {
        Optional<OrderStatus> latest = orderStatusRepository.findLatestStatusByOrderId(order.getId());
        OrderStatusCd from = latest.map(OrderStatus::getStatusCd).orElse(null);

        if (!canMoveTo(from, to)) {
            log.error("Can't move order [{}] status from {} to {}", order.getId(),from, to);
            throw new InternalErrorException();
        }

        OrderStatus orderStatus = new OrderStatus();
        orderStatus.setOrderId(order.getId());
        orderStatus.setStatusDate(Instant.now());
        orderStatus.setStatusCd(to);

        return orderStatusRepository.save(orderStatus);
    }
}
