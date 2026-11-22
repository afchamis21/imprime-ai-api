package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.support.AddSupportMessageRequest;
import org.imprime.ai.api.http.request.support.CreateSupportTicketRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.support.SupportTicketDetailResponse;
import org.imprime.ai.api.http.response.support.SupportTicketMessageResponse;
import org.imprime.ai.api.http.response.support.SupportTicketSummaryResponse;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.SupportTicketMessage;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.SupportTicketMessageRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.SupportTicketService;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageableSortMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportTicketServiceImpl implements SupportTicketService {
    private static final Map<String, String> SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "updatedAt", "updateDt",
            "lastUpdatedAt", "updateDt",
            "protocolCode", "protocolCode"
    );
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updateDt", "createDt");

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final JsonCodec jsonCodec = new JsonCodec();

    @Override
    public PageResponse<SupportTicketSummaryResponse> listTickets(SupportTicketStatus status, Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        Pageable mapped = PageableSortMapper.remap(pageable, SORT_MAPPING, DEFAULT_SORT);

        Page<SupportTicket> page = status == null
                ? supportTicketRepository.findAllByUserIdAndStatus(caller.getId(), StatusCd.ACTIVE, mapped)
                : supportTicketRepository.findAllByUserIdAndTicketStatusAndStatus(caller.getId(), status, StatusCd.ACTIVE, mapped);

        Set<Long> orderIds = page.getContent().stream()
                .map(SupportTicket::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> orderGuidsById = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Order::getGuid, (a, b) -> a));

        Page<SupportTicketSummaryResponse> mappedPage = page.map(ticket -> {
            int messagesCount = (int) messageRepository.countBySupportTicketIdAndStatus(ticket.getId(), StatusCd.ACTIVE);
            String orderGuid = ticket.getOrderId() != null ? orderGuidsById.get(ticket.getOrderId()) : null;
            Instant lastUpdatedAt = ticket.getUpdateDt() != null ? ticket.getUpdateDt() : ticket.getCreateDt();
            return new SupportTicketSummaryResponse(
                    ticket.getGuid(),
                    ticket.getProtocolCode(),
                    ticket.getSubject(),
                    ticket.getCategory(),
                    ticket.getTicketStatus(),
                    orderGuid,
                    messagesCount,
                    ticket.getCreateDt(),
                    lastUpdatedAt
            );
        });

        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public SupportTicketDetailResponse createTicket(CreateSupportTicketRequest request) {
        User caller = ServiceContext.getUserOrThrow();

        Long orderId = null;
        String resolvedOrderGuid = null;
        if (request.orderGuid() != null && !request.orderGuid().isBlank()) {
            String trimmedOrderGuid = request.orderGuid().trim();
            Order order = orderRepository.findOrderByGuid(trimmedOrderGuid)
                    .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));

            boolean isBuyer = caller.getId().equals(order.getBuyerId());
            boolean isVendor = caller.getCompanyId() != null && caller.getCompanyId().equals(order.getVendorId());
            if (!isBuyer && !isVendor) {
                throw new ForbiddenException();
            }
            orderId = order.getId();
            resolvedOrderGuid = order.getGuid();
        }

        boolean dispute = Boolean.TRUE.equals(request.isDispute())
                || SupportTicketCategory.DISPUTE.equals(request.category());
        List<String> attachments = sanitizeAttachments(request.attachments());

        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(caller.getId());
        ticket.setOrderId(orderId);
        ticket.setProtocolCode(generateUniqueProtocolCode());
        ticket.setSubject(request.subject().trim());
        ticket.setCategory(request.category());
        ticket.setTicketStatus(SupportTicketStatus.OPEN);
        ticket.setIsDispute(dispute);
        ticket.setPriority(dispute ? TicketPriority.HIGH : TicketPriority.MEDIUM);

        SupportTicket savedTicket = supportTicketRepository.save(ticket);

        SupportTicketMessage initialMessage = new SupportTicketMessage();
        initialMessage.setSupportTicketId(savedTicket.getId());
        initialMessage.setSenderUserId(caller.getId());
        initialMessage.setFromSupport(Boolean.FALSE);
        initialMessage.setMessage(request.message().trim());
        initialMessage.setAttachmentsJson(jsonCodec.toJsonArray(attachments));

        SupportTicketMessage savedMessage = messageRepository.save(initialMessage);
        SupportTicketMessageResponse messageDto = new SupportTicketMessageResponse(
                savedMessage.getGuid(),
                caller.getGuid(),
                caller.getFullName(),
                false,
                savedMessage.getMessage(),
                savedMessage.getCreateDt(),
                attachments
        );

        Instant lastUpdatedAt = savedTicket.getUpdateDt() != null ? savedTicket.getUpdateDt() : savedTicket.getCreateDt();
        return new SupportTicketDetailResponse(
                savedTicket.getGuid(),
                savedTicket.getProtocolCode(),
                savedTicket.getSubject(),
                savedTicket.getCategory(),
                savedTicket.getTicketStatus(),
                resolvedOrderGuid,
                List.of(messageDto),
                savedTicket.getCreateDt(),
                lastUpdatedAt
        );
    }

    @Override
    public SupportTicketDetailResponse getTicketDetail(String ticketGuid) {
        User caller = ServiceContext.getUserOrThrow();
        SupportTicket ticket = loadAuthorizedTicket(ticketGuid, caller);

        String orderGuid = ticket.getOrderId() != null
                ? orderRepository.findById(ticket.getOrderId()).map(Order::getGuid).orElse(null)
                : null;

        List<SupportTicketMessage> messages = messageRepository
                .findAllBySupportTicketIdAndStatusOrderByCreateDtAsc(ticket.getId(), StatusCd.ACTIVE);

        Set<Long> senderIds = messages.stream()
                .map(SupportTicketMessage::getSenderUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> sendersById = senderIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<SupportTicketMessageResponse> messageDtos = messages.stream()
                .map(msg -> toMessageResponse(msg, sendersById.get(msg.getSenderUserId())))
                .toList();

        Instant lastUpdatedAt = ticket.getUpdateDt() != null ? ticket.getUpdateDt() : ticket.getCreateDt();
        return new SupportTicketDetailResponse(
                ticket.getGuid(),
                ticket.getProtocolCode(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getTicketStatus(),
                orderGuid,
                messageDtos,
                ticket.getCreateDt(),
                lastUpdatedAt
        );
    }

    @Override
    @Transactional
    public SupportTicketMessageResponse addMessage(String ticketGuid, AddSupportMessageRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        SupportTicket ticket = loadAuthorizedTicket(ticketGuid, caller);

        if (SupportTicketStatus.CLOSED.equals(ticket.getTicketStatus())) {
            throw new BadRequestException(MessageCd.SUPPORT_TICKET_CLOSED);
        }

        if (SupportTicketStatus.WAITING_USER.equals(ticket.getTicketStatus())
                || SupportTicketStatus.RESOLVED.equals(ticket.getTicketStatus())) {
            ticket.setTicketStatus(SupportTicketStatus.OPEN);
        }
        ticket.setUpdateDt(Instant.now());
        supportTicketRepository.save(ticket);

        List<String> attachments = sanitizeAttachments(request.attachments());

        SupportTicketMessage message = new SupportTicketMessage();
        message.setSupportTicketId(ticket.getId());
        message.setSenderUserId(caller.getId());
        message.setFromSupport(Boolean.FALSE);
        message.setMessage(request.message().trim());
        message.setAttachmentsJson(jsonCodec.toJsonArray(attachments));

        SupportTicketMessage saved = messageRepository.save(message);
        return toMessageResponse(saved, caller);
    }

    private SupportTicket loadAuthorizedTicket(String ticketGuid, User caller) {
        SupportTicket ticket = supportTicketRepository.findByGuidAndStatus(ticketGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.SUPPORT_TICKET_NOT_FOUND));
        if (!Objects.equals(ticket.getUserId(), caller.getId())) {
            throw new ForbiddenException();
        }
        return ticket;
    }

    private SupportTicketMessageResponse toMessageResponse(SupportTicketMessage message, User sender) {
        boolean fromSupport = Boolean.TRUE.equals(message.getFromSupport());
        String senderName = fromSupport
                ? "Equipe Imprime AÍ"
                : (sender != null ? sender.getFullName() : "Usuário");
        List<String> attachments = jsonCodec.readStringList(message.getAttachmentsJson());
        return new SupportTicketMessageResponse(
                message.getGuid(),
                sender != null ? sender.getGuid() : null,
                senderName,
                fromSupport,
                message.getMessage(),
                message.getCreateDt(),
                attachments
        );
    }

    private List<String> sanitizeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .distinct()
                .limit(10)
                .toList();
    }

    private String generateUniqueProtocolCode() {
        String candidate;
        do {
            String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
            candidate = "SUP-" + raw;
        } while (supportTicketRepository.existsByProtocolCode(candidate));
        return candidate;
    }
}
