package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminTicketReplyRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateTicketRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderChatMessageResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderChatTranscriptResponse;
import org.imprime.ai.api.http.response.admin.AdminTicketDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminTicketSummaryResponse;
import org.imprime.ai.api.http.response.support.SupportTicketMessageResponse;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.SupportTicketMessage;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.notification.event.SupportTicketNotificationEvent;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.SupportTicketMessageRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.AdminTicketService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {
    private static final String SUPPORT_SENDER_NAME = "Equipe Imprime AÍ";

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OrderViewAssembler orderViewAssembler;
    private final AdminAuditService adminAuditService;
    private final JsonCodec jsonCodec;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Override
    public PageResponse<AdminTicketSummaryResponse> listTickets(
            String q,
            SupportTicketStatus status,
            SupportTicketCategory category,
            TicketPriority priority,
            Boolean isDispute,
            String assignedAdminGuid,
            Pageable pageable
    ) {
        List<SupportTicket> all = supportTicketRepository.findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd.ACTIVE);
        if (all.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        String normalizedAssigned = trimToNull(assignedAdminGuid);
        Long filterAdminId = null;
        boolean filterUnassigned = false;
        if (normalizedAssigned != null) {
            if ("UNASSIGNED".equalsIgnoreCase(normalizedAssigned)) {
                filterUnassigned = true;
            } else {
                filterAdminId = userRepository.findUserByGuid(normalizedAssigned)
                        .map(User::getId)
                        .orElse(-1L);
            }
        }

        List<SupportTicket> filteredTickets = new ArrayList<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();

        for (SupportTicket ticket : all) {
            if (status != null && !status.equals(ticket.getTicketStatus())) {
                continue;
            }
            if (category != null && !category.equals(ticket.getCategory())) {
                continue;
            }
            TicketPriority effectivePriority = ticket.getPriority() != null ? ticket.getPriority() : TicketPriority.MEDIUM;
            if (priority != null && !priority.equals(effectivePriority)) {
                continue;
            }
            boolean disputeFlag = Boolean.TRUE.equals(ticket.getIsDispute());
            if (isDispute != null && disputeFlag != isDispute) {
                continue;
            }
            if (filterUnassigned && ticket.getAssignedAdminId() != null) {
                continue;
            }
            if (filterAdminId != null && !Objects.equals(filterAdminId, ticket.getAssignedAdminId())) {
                continue;
            }
            filteredTickets.add(ticket);
            if (ticket.getUserId() != null) userIds.add(ticket.getUserId());
            if (ticket.getAssignedAdminId() != null) userIds.add(ticket.getAssignedAdminId());
            if (ticket.getOrderId() != null) orderIds.add(ticket.getOrderId());
        }

        if (filteredTickets.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Map<Long, Order> ordersById = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));

        String normalizedQuery = trimToNull(q);
        List<AdminTicketSummaryResponse> items = new ArrayList<>();
        for (SupportTicket ticket : filteredTickets) {
            User requester = ticket.getUserId() != null ? usersById.get(ticket.getUserId()) : null;
            User assignedAdmin = ticket.getAssignedAdminId() != null ? usersById.get(ticket.getAssignedAdminId()) : null;
            Order order = ticket.getOrderId() != null ? ordersById.get(ticket.getOrderId()) : null;
            String orderCode = resolveOrderCode(order);

            if (!matchesQuery(ticket, requester, order, orderCode, normalizedQuery)) {
                continue;
            }

            List<SupportTicketMessage> messages = messageRepository
                    .findAllBySupportTicketIdAndStatusOrderByCreateDtAsc(ticket.getId(), StatusCd.ACTIVE);
            int evidenceCount = 0;
            for (SupportTicketMessage msg : messages) {
                evidenceCount += jsonCodec.readStringList(msg.getAttachmentsJson()).size();
            }

            Instant lastUpdatedAt = ticket.getUpdateDt() != null ? ticket.getUpdateDt() : ticket.getCreateDt();
            items.add(new AdminTicketSummaryResponse(
                    ticket.getGuid(),
                    ticket.getProtocolCode(),
                    ticket.getSubject(),
                    ticket.getCategory(),
                    ticket.getTicketStatus(),
                    ticket.getPriority() != null ? ticket.getPriority() : TicketPriority.MEDIUM,
                    Boolean.TRUE.equals(ticket.getIsDispute()),
                    requester != null ? requester.getGuid() : null,
                    requester != null ? requester.getFullName() : "Usuário",
                    requester != null ? requester.getEmail() : null,
                    order != null ? order.getGuid() : null,
                    orderCode,
                    assignedAdmin != null ? assignedAdmin.getGuid() : null,
                    assignedAdmin != null ? assignedAdmin.getFullName() : null,
                    messages.size(),
                    evidenceCount,
                    ticket.getCreateDt(),
                    lastUpdatedAt
            ));
        }

        return PageUtils.paginate(items, pageable);
    }

    @Override
    public AdminTicketDetailResponse getTicketDetail(String ticketGuid) {
        SupportTicket ticket = requireActiveTicket(ticketGuid);
        return buildDetailResponse(ticket);
    }

    @Override
    @Transactional
    public AdminTicketDetailResponse replyTicket(String ticketGuid, AdminTicketReplyRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        SupportTicket ticket = requireActiveTicket(ticketGuid);

        if (SupportTicketStatus.CLOSED.equals(ticket.getTicketStatus()) && request.nextStatus() == null) {
            throw new BadRequestException(MessageCd.SUPPORT_TICKET_CLOSED);
        }

        Map<String, Object> beforeState = snapshotTicketState(ticket);
        List<String> attachments = sanitizeAttachments(request.attachments());
        String trimmedMessage = request.message().trim();

        SupportTicketMessage message = new SupportTicketMessage();
        message.setSupportTicketId(ticket.getId());
        message.setSenderUserId(admin.getId());
        message.setFromSupport(Boolean.TRUE);
        message.setMessage(trimmedMessage);
        message.setAttachmentsJson(jsonCodec.toJsonArray(attachments));
        messageRepository.save(message);

        SupportTicketStatus nextStatus = request.nextStatus();
        if (nextStatus == null) {
            if (SupportTicketStatus.OPEN.equals(ticket.getTicketStatus())
                    || SupportTicketStatus.IN_REVIEW.equals(ticket.getTicketStatus())) {
                nextStatus = SupportTicketStatus.WAITING_USER;
            }
        }
        if (nextStatus != null) {
            applyStatusAndResolvedTimestamp(ticket, nextStatus);
        }
        if (ticket.getAssignedAdminId() == null) {
            ticket.setAssignedAdminId(admin.getId());
        }
        ticket.setUpdateDt(Instant.now());
        SupportTicket saved = supportTicketRepository.save(ticket);

        Map<String, Object> afterState = new LinkedHashMap<>(snapshotTicketState(saved));
        afterState.put("replyAttachmentsCount", attachments.size());
        adminAuditService.record(
                AuditAction.TICKET_REPLIED,
                "SUPPORT_TICKET",
                saved.getGuid(),
                trimmedMessage,
                beforeState,
                afterState
        );

        publishTicketEvent(saved, "REPLIED", trimmedMessage);

        return buildDetailResponse(saved);
    }

    @Override
    @Transactional
    public AdminTicketDetailResponse updateTicket(String ticketGuid, AdminUpdateTicketRequest request) {
        SupportTicket ticket = requireActiveTicket(ticketGuid);
        Map<String, Object> beforeState = snapshotTicketState(ticket);

        if (request.status() != null) {
            applyStatusAndResolvedTimestamp(ticket, request.status());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.assignedAdminGuid() != null) {
            String trimmedGuid = request.assignedAdminGuid().trim();
            if (trimmedGuid.isEmpty()) {
                ticket.setAssignedAdminId(null);
            } else {
                User assignee = userRepository.findUserByGuid(trimmedGuid)
                        .filter(u -> !StatusCd.DELETED.equals(u.getStatus()))
                        .orElseThrow(() -> new NotFoundException(MessageCd.USER_NOT_FOUND));
                if (!assignee.isAdmin()) {
                    throw new BadRequestException(MessageCd.ADMIN_ROLE_REQUIRED);
                }
                ticket.setAssignedAdminId(assignee.getId());
            }
        }
        if (request.resolutionNotes() != null) {
            ticket.setResolutionNotes(trimToNull(request.resolutionNotes()));
        }

        ticket.setUpdateDt(Instant.now());
        SupportTicket saved = supportTicketRepository.save(ticket);

        String auditReason = trimToNull(request.reason());
        if (auditReason == null) {
            auditReason = trimToNull(request.resolutionNotes());
        }
        if (auditReason == null) {
            auditReason = "Atualização administrativa do ticket";
        }

        adminAuditService.record(
                AuditAction.TICKET_UPDATED,
                "SUPPORT_TICKET",
                saved.getGuid(),
                auditReason,
                beforeState,
                snapshotTicketState(saved)
        );

        if (request.status() != null || request.resolutionNotes() != null) {
            publishTicketEvent(saved, "UPDATED", null);
        }

        return buildDetailResponse(saved);
    }

    private void publishTicketEvent(SupportTicket saved, String action, String replyExcerpt) {
        if (eventPublisher == null || saved == null) {
            return;
        }
        eventPublisher.publishEvent(new SupportTicketNotificationEvent(
                saved.getGuid(),
                saved.getProtocolCode(),
                saved.getUserId(),
                saved.getSubject(),
                saved.getCategory() != null ? saved.getCategory().name() : null,
                saved.getTicketStatus() != null ? saved.getTicketStatus().name() : null,
                Boolean.TRUE.equals(saved.getIsDispute()),
                action,
                replyExcerpt,
                saved.getResolutionNotes()
        ));
    }

    private AdminTicketDetailResponse buildDetailResponse(SupportTicket ticket) {
        List<SupportTicketMessage> messages = messageRepository
                .findAllBySupportTicketIdAndStatusOrderByCreateDtAsc(ticket.getId(), StatusCd.ACTIVE);

        Set<Long> userIds = new HashSet<>();
        if (ticket.getUserId() != null) userIds.add(ticket.getUserId());
        if (ticket.getAssignedAdminId() != null) userIds.add(ticket.getAssignedAdminId());
        for (SupportTicketMessage msg : messages) {
            if (msg.getSenderUserId() != null) userIds.add(msg.getSenderUserId());
        }

        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        User requester = ticket.getUserId() != null ? usersById.get(ticket.getUserId()) : null;
        User assignedAdmin = ticket.getAssignedAdminId() != null ? usersById.get(ticket.getAssignedAdminId()) : null;

        Order order = ticket.getOrderId() != null
                ? orderRepository.findById(ticket.getOrderId()).orElse(null)
                : null;
        String orderCode = resolveOrderCode(order);
        OrderStatusCd orderStatus = null;
        if (order != null && order.getId() != null) {
            orderStatus = orderStatusRepository.findLatestStatusByOrderId(order.getId())
                    .map(OrderStatus::getStatusCd)
                    .orElse(OrderStatusCd.CREATED);
        }

        Set<String> evidenceSet = new LinkedHashSet<>();
        List<SupportTicketMessageResponse> messageResponses = new ArrayList<>(messages.size());
        for (SupportTicketMessage msg : messages) {
            List<String> attachments = jsonCodec.readStringList(msg.getAttachmentsJson());
            evidenceSet.addAll(attachments);
            User sender = msg.getSenderUserId() != null ? usersById.get(msg.getSenderUserId()) : null;
            boolean fromSupport = Boolean.TRUE.equals(msg.getFromSupport());
            String senderName = fromSupport
                    ? (sender != null ? sender.getFullName() + " (" + SUPPORT_SENDER_NAME + ")" : SUPPORT_SENDER_NAME)
                    : (sender != null ? sender.getFullName() : "Usuário");
            messageResponses.add(new SupportTicketMessageResponse(
                    msg.getGuid(),
                    sender != null ? sender.getGuid() : null,
                    senderName,
                    fromSupport,
                    msg.getMessage(),
                    msg.getCreateDt(),
                    attachments
            ));
        }

        List<AdminOrderChatTranscriptResponse> orderChats = loadOrderChatTranscripts(order);
        Instant lastUpdatedAt = ticket.getUpdateDt() != null ? ticket.getUpdateDt() : ticket.getCreateDt();

        return new AdminTicketDetailResponse(
                ticket.getGuid(),
                ticket.getProtocolCode(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getTicketStatus(),
                ticket.getPriority() != null ? ticket.getPriority() : TicketPriority.MEDIUM,
                Boolean.TRUE.equals(ticket.getIsDispute()),
                requester != null ? requester.getGuid() : null,
                requester != null ? requester.getFullName() : "Usuário",
                requester != null ? requester.getEmail() : null,
                order != null ? order.getGuid() : null,
                orderCode,
                orderStatus,
                assignedAdmin != null ? assignedAdmin.getGuid() : null,
                assignedAdmin != null ? assignedAdmin.getFullName() : null,
                ticket.getResolutionNotes(),
                ticket.getResolvedAt(),
                messageResponses,
                List.copyOf(evidenceSet),
                orderChats,
                ticket.getCreateDt(),
                lastUpdatedAt
        );
    }

    private List<AdminOrderChatTranscriptResponse> loadOrderChatTranscripts(Order order) {
        if (order == null || order.getGuid() == null) {
            return List.of();
        }
        List<Chat> chats = chatRepository.findAllByOrderGuid(order.getGuid());
        if (chats.isEmpty()) {
            return List.of();
        }

        Map<String, User> usersByGuidCache = new HashMap<>();
        Map<String, Company> companiesByGuidCache = new HashMap<>();
        List<AdminOrderChatTranscriptResponse> transcripts = new ArrayList<>(chats.size());

        for (Chat chat : chats) {
            User client = resolveUserByGuid(chat.getClientGuid(), usersByGuidCache);
            Company company = resolveCompanyByGuid(chat.getCompanyGuid(), companiesByGuidCache);
            List<ChatMessage> chatMessages = chatMessageRepository.findAllByChatIdOrderByCreateDtAsc(chat.getChatId());

            List<AdminOrderChatMessageResponse> messageDtos = new ArrayList<>(chatMessages.size());
            for (ChatMessage cm : chatMessages) {
                String senderId = cm.getSenderId();
                String senderName;
                String senderRole;
                if (senderId != null && senderId.equals(chat.getCompanyGuid())) {
                    senderName = company != null ? company.getName() : "Maker";
                    senderRole = "MAKER";
                } else {
                    User sender = resolveUserByGuid(senderId, usersByGuidCache);
                    if (sender != null) {
                        senderName = sender.getFullName();
                        senderRole = sender.getRole() != null ? sender.getRole().name() : "USER";
                    } else {
                        senderName = company != null && Objects.equals(senderId, chat.getClientGuid())
                                ? "Comprador"
                                : "Sistema";
                        senderRole = "USER";
                    }
                }
                messageDtos.add(new AdminOrderChatMessageResponse(
                        cm.getGuid(),
                        senderId,
                        senderName,
                        senderRole,
                        cm.getContent(),
                        cm.getType(),
                        cm.getCreateDt()
                ));
            }

            transcripts.add(new AdminOrderChatTranscriptResponse(
                    chat.getChatId(),
                    chat.getOrderGuid(),
                    chat.getClientGuid(),
                    client != null ? client.getFullName() : "Comprador",
                    chat.getCompanyGuid(),
                    company != null ? company.getName() : "Maker",
                    chat.getLastMessageAt(),
                    messageDtos
            ));
        }

        return transcripts;
    }

    private User resolveUserByGuid(String guid, Map<String, User> cache) {
        if (guid == null || guid.isBlank()) {
            return null;
        }
        if (cache.containsKey(guid)) {
            return cache.get(guid);
        }
        User resolved = userRepository.findUserByGuid(guid).orElse(null);
        cache.put(guid, resolved);
        return resolved;
    }

    private Company resolveCompanyByGuid(String guid, Map<String, Company> cache) {
        if (guid == null || guid.isBlank()) {
            return null;
        }
        if (cache.containsKey(guid)) {
            return cache.get(guid);
        }
        Company resolved = companyRepository.findByGuid(guid).orElse(null);
        cache.put(guid, resolved);
        return resolved;
    }

    private SupportTicket requireActiveTicket(String ticketGuid) {
        return supportTicketRepository.findByGuidAndStatus(ticketGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.SUPPORT_TICKET_NOT_FOUND));
    }

    private void applyStatusAndResolvedTimestamp(SupportTicket ticket, SupportTicketStatus status) {
        ticket.setTicketStatus(status);
        if (SupportTicketStatus.RESOLVED.equals(status) || SupportTicketStatus.CLOSED.equals(status)) {
            if (ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(Instant.now());
            }
        } else {
            ticket.setResolvedAt(null);
        }
    }

    private Map<String, Object> snapshotTicketState(SupportTicket ticket) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("ticketGuid", ticket.getGuid());
        state.put("status", ticket.getTicketStatus() != null ? ticket.getTicketStatus().name() : null);
        state.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : null);
        state.put("assignedAdminId", ticket.getAssignedAdminId());
        state.put("resolutionNotes", ticket.getResolutionNotes());
        state.put("resolvedAt", ticket.getResolvedAt() != null ? ticket.getResolvedAt().toString() : null);
        return state;
    }

    private String resolveOrderCode(Order order) {
        if (order == null) {
            return null;
        }
        return (order.getOrderCode() != null && !order.getOrderCode().isBlank())
                ? order.getOrderCode()
                : orderViewAssembler.formatOrderCode(order.getGuid());
    }

    private boolean matchesQuery(
            SupportTicket ticket,
            User requester,
            Order order,
            String orderCode,
            String query
    ) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(ticket.getProtocolCode(), q)
                || contains(ticket.getSubject(), q)
                || contains(ticket.getGuid(), q)
                || (requester != null && (contains(requester.getFullName(), q) || contains(requester.getEmail(), q)))
                || (order != null && (contains(order.getGuid(), q) || contains(orderCode, q)));
    }

    private boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
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

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
