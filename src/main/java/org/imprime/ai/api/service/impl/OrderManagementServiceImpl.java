package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.OrderManagementService;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManagementServiceImpl implements OrderManagementService {
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final ChatRepository chatRepository;
    private final CompanyRepository companyRepository;
    private final OrderViewAssembler orderViewAssembler;

    @Override
    public OrderDetailResponse getOrderDetail(String orderGuid) {
        User caller = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findOrderByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));

        boolean isBuyer = caller.getId().equals(order.getBuyerId());
        boolean isVendor = caller.getCompanyId() != null && caller.getCompanyId().equals(order.getVendorId());
        boolean isQuotingChatMaker = !isBuyer && !isVendor && isMakerParticipatingInQuotingChat(caller, order);

        if (!isBuyer && !isVendor && !isQuotingChatMaker) {
            log.warn("User [{}] attempted unauthorized read on Order [{}]", caller.getGuid(), orderGuid);
            throw new ForbiddenException();
        }

        return orderViewAssembler.toDetailResponse(order);
    }

    @Override
    @Transactional
    public OrderDetailResponse updateOrderStatus(String orderGuid, UpdateOrderStatusRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findOrderByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));

        boolean isBuyer = caller.getId().equals(order.getBuyerId());
        boolean isMaker = caller.getCompanyId() != null && caller.getCompanyId().equals(order.getVendorId());

        if (!isBuyer && !isMaker) {
            log.warn("User [{}] attempted unauthorized status transition on Order [{}]", caller.getGuid(), orderGuid);
            throw new ForbiddenException();
        }

        OrderStatusCd current = orderStatusService.findLatest(order).getStatusCd();
        OrderStatusCd target = request.status();

        if (!orderStatusService.canMoveTo(current, target)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        boolean permittedForRole = (isBuyer && isBuyerTransitionAllowed(current, target))
                || (isMaker && isMakerTransitionAllowed(current, target));
        if (!permittedForRole) {
            log.warn("User [{}] (buyer={}, maker={}) is not allowed to move Order [{}] from {} to {}",
                    caller.getGuid(), isBuyer, isMaker, orderGuid, current, target);
            throw new ForbiddenException();
        }

        boolean updatedOrder = false;
        if (OrderStatusCd.IN_TRANSIT.equals(target)
                && request.trackingCode() != null && !request.trackingCode().isBlank()) {
            order.setTrackingCode(request.trackingCode().trim());
            updatedOrder = true;
        }
        if ((OrderStatusCd.CANCELLED.equals(target) || OrderStatusCd.REFUNDED.equals(target))
                && request.reason() != null && !request.reason().isBlank()) {
            order.setCancellationReason(request.reason().trim());
            updatedOrder = true;
        }
        if (updatedOrder) {
            orderRepository.save(order);
        }

        orderStatusService.move(order, target);
        return orderViewAssembler.toDetailResponse(order);
    }

    private boolean isMakerParticipatingInQuotingChat(User caller, Order order) {
        if (caller.getCompanyId() == null || order.getVendorId() != null) {
            return false;
        }
        OrderStatusCd latestStatus = orderStatusService.findLatest(order).getStatusCd();
        if (!OrderStatusCd.QUOTING.equals(latestStatus)) {
            return false;
        }
        Optional<Company> companyOpt = companyRepository.findById(caller.getCompanyId());
        return companyOpt.isPresent()
                && chatRepository.existsByOrderGuidAndCompanyGuid(order.getGuid(), companyOpt.get().getGuid());
    }

    private boolean isBuyerTransitionAllowed(OrderStatusCd from, OrderStatusCd to) {
        return switch (from) {
            case CREATED, QUOTING -> OrderStatusCd.CANCELLED.equals(to);
            case CONFIRMED -> OrderStatusCd.IN_PRODUCTION.equals(to) || OrderStatusCd.CANCELLED.equals(to);
            case IN_TRANSIT -> OrderStatusCd.COMPLETED.equals(to);
            case COMPLETED, CANCELLED -> OrderStatusCd.REFUNDED.equals(to);
            default -> false;
        };
    }

    private boolean isMakerTransitionAllowed(OrderStatusCd from, OrderStatusCd to) {
        return switch (from) {
            case CONFIRMED -> OrderStatusCd.IN_PRODUCTION.equals(to) || OrderStatusCd.CANCELLED.equals(to);
            case IN_PRODUCTION -> OrderStatusCd.IN_TRANSIT.equals(to) || OrderStatusCd.CANCELLED.equals(to);
            case COMPLETED, CANCELLED -> OrderStatusCd.REFUNDED.equals(to);
            default -> false;
        };
    }
}
