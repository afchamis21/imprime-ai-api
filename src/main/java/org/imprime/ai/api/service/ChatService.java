package org.imprime.ai.api.service;

import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.chat.CreateChatOfferRequest;
import org.imprime.ai.api.http.request.chat.QuoteDecisionRequest;
import org.imprime.ai.api.http.request.chat.SendMessageRequest;
import org.imprime.ai.api.http.request.chat.StartChatRequest;
import org.imprime.ai.api.model.*;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.notification.event.ChatMessageRecordedEvent;
import org.imprime.ai.api.notification.event.QuoteAcceptedNotificationEvent;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.OrderShippingQuoteRepository;
import org.imprime.ai.api.websocket.chat.ChatEvent;
import org.imprime.ai.api.websocket.chat.ChatEventToPublish;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final CompanyService companyService;
    private final OrderService orderService;
    private final OrderStatusService orderStatusService;

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;

    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;
    private final MakerOrderComplianceService makerCompliance;

    @Autowired(required = false)
    private OrderShippingQuoteRepository shippingQuoteRepository;

    @Autowired(required = false)
    private AddressRepository addressRepository;

    @Transactional
    public void start(StartChatRequest request) {
        User caller = ServiceContext.getUserOrThrow();

        Optional<Order> optionalOrder = orderService.findByGuid(request.orderGuid());
        Order order = optionalOrder.orElseThrow(() -> new BadRequestException(MessageCd.ORDER_NOT_FOUND));

        if (!order.getBuyerId().equals(caller.getId())) {
            log.warn("User trying to start chat on Order [{}]", order.getId());
            throw new ForbiddenException();
        }

        orderService.requireDeclarationsForExistingFiles(order, caller);
        OrderStatus latest = orderStatusService.findLatest(order);
        if (!OrderStatusCd.QUOTING.equals(latest.getStatusCd())) {
            log.info("Order [{}] is already in status [{}]; cloning a new QUOTING order for buyer [{}]",
                    order.getGuid(), latest.getStatusCd(), caller.getGuid());
            order = orderService.cloneOrderForNewQuote(order, caller);
        }

        Optional<Company> optionalCompany = companyService.findByCompanyGuid(request.makerGuid());
        Company company = optionalCompany.orElseThrow(() -> new BadRequestException(MessageCd.COMPANY_NOT_FOUND));

        final String targetOrderGuid = order.getGuid();
        Chat chat = chatRepository
                .findFirstByOrderGuidAndCompanyGuidAndStatusOrderByCreateDtDesc(
                        targetOrderGuid, company.getGuid(), StatusCd.ACTIVE
                )
                .orElseGet(() -> {
                    Chat created = new Chat();
                    created.setOrderGuid(targetOrderGuid);
                    created.setClientGuid(caller.getGuid());
                    created.setCompanyGuid(company.getGuid());
                    created.setLastMessageAt(Instant.now());
                    return chatRepository.saveAndFlush(created);
                });

        sendMessage(chat, request.message(), getChatMembers(chat));
    }

    @Transactional
    public MessageDTO createOffer(UUID chatId, CreateChatOfferRequest request) {
        if (request == null || request.serviceAmount() == null || request.serviceAmount().signum() <= 0) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        Chat chat = findChat(chatId);
        if (!StatusCd.ACTIVE.equals(chat.getStatus())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        List<String> recipients = getChatMembers(chat);
        SendMessageRequest offerRequest = new SendMessageRequest(
                request.serviceAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                MessageType.OFFER,
                request.productionDays(),
                request.packageWeight(),
                request.packageHeight(),
                request.packageWidth(),
                request.packageLength()
        );
        MessageDTO offerDto = sendMessageAndReturn(chat, offerRequest, recipients);
        if (request.note() != null && !request.note().isBlank()) {
            sendMessageAndReturn(chat, new SendMessageRequest(request.note().trim(), MessageType.MESSAGE), recipients);
        }
        return offerDto;
    }

    @Transactional
    public void sendMessage(UUID chatId, SendMessageRequest request) {
        Chat chat = findChat(chatId);

        if (!StatusCd.ACTIVE.equals(chat.getStatus())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        sendMessageAndReturn(chat, request, null);
    }

    @Transactional
    public void sendMessage(Chat chat, SendMessageRequest request) {
        sendMessageAndReturn(chat, request, null);
    }

    @Transactional
    public void sendMessage(Chat chat, SendMessageRequest request, @Nullable List<String> recipients) {
        sendMessageAndReturn(chat, request, recipients);
    }

    @Transactional
    public MessageDTO sendMessageAndReturn(Chat chat, SendMessageRequest request, @Nullable List<String> recipients) {
        User caller = ServiceContext.getUserOrThrow();

        String normalizedContent = request.content();
        Integer productionDays = null;
        BigDecimal packageWeight = null;
        BigDecimal packageHeight = null;
        BigDecimal packageWidth = null;
        BigDecimal packageLength = null;

        if (MessageType.OFFER.equals(request.type())) {
            Order order = makerCompliance.lockOrder(chat.getOrderGuid());
            if (orderStatusService.findLatest(order).getStatusCd() != OrderStatusCd.QUOTING) {
                throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
            }
            Company maker = companyService.findByCompanyGuid(chat.getCompanyGuid()).orElseThrow();
            if (!StatusCd.ACTIVE.equals(maker.getStatus())) throw new ForbiddenException();
            makerCompliance.requireNoRefusal(order.getId(), maker.getId());
            boolean isBuyer = Objects.equals(chat.getClientGuid(), caller.getGuid());
            if (!isBuyer) {
                makerCompliance.requireQuoteReview(order.getId(), maker.getId(), caller.getId());
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(request.content() == null ? "" : request.content().trim());
            } catch (Exception e) {
                log.warn("Offer Message sent but value is not numeric!");
                throw new BadRequestException(MessageCd.OFFER_NOT_NUMERIC);
            }
            if (amount.signum() <= 0) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
            normalizedContent = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();

            productionDays = request.productionDays();
            packageWeight = request.packageWeight();
            packageHeight = request.packageHeight();
            packageWidth = request.packageWidth();
            packageLength = request.packageLength();

            if (!isBuyer) {
                if (!isValidPositiveInt(productionDays)
                        || !isValidPositiveDecimal(packageWeight)
                        || !isValidPositiveDecimal(packageHeight)
                        || !isValidPositiveDecimal(packageWidth)
                        || !isValidPositiveDecimal(packageLength)) {
                    throw new BadRequestException(MessageCd.VALIDATION_ERROR);
                }
            } else {
                if ((productionDays != null && productionDays <= 0)
                        || (packageWeight != null && packageWeight.signum() <= 0)
                        || (packageHeight != null && packageHeight.signum() <= 0)
                        || (packageWidth != null && packageWidth.signum() <= 0)
                        || (packageLength != null && packageLength.signum() <= 0)) {
                    throw new BadRequestException(MessageCd.VALIDATION_ERROR);
                }
                if (productionDays == null || packageWeight == null || packageHeight == null
                        || packageWidth == null || packageLength == null) {
                    Optional<ChatMessage> previousOpt = chatMessageRepository
                            .findTopByChatIdAndTypeAndPackageWeightIsNotNullOrderByCreateDtDesc(chat.getChatId(), MessageType.OFFER);
                    if (previousOpt != null && previousOpt.isPresent()) {
                        ChatMessage prev = previousOpt.get();
                        if (productionDays == null) productionDays = prev.getProductionDays();
                        if (packageWeight == null) packageWeight = prev.getPackageWeight();
                        if (packageHeight == null) packageHeight = prev.getPackageHeight();
                        if (packageWidth == null) packageWidth = prev.getPackageWidth();
                        if (packageLength == null) packageLength = prev.getPackageLength();
                    }
                }
            }

            if (packageWeight != null) packageWeight = packageWeight.setScale(3, RoundingMode.HALF_UP);
            if (packageHeight != null) packageHeight = packageHeight.setScale(2, RoundingMode.HALF_UP);
            if (packageWidth != null) packageWidth = packageWidth.setScale(2, RoundingMode.HALF_UP);
            if (packageLength != null) packageLength = packageLength.setScale(2, RoundingMode.HALF_UP);
        }

        ChatMessage message = new ChatMessage();
        message.setChatId(chat.getChatId());
        message.setSenderId(caller.getGuid());
        message.setContent(normalizedContent);
        message.setType(request.type());
        message.setProductionDays(productionDays);
        message.setPackageWeight(packageWeight);
        message.setPackageHeight(packageHeight);
        message.setPackageWidth(packageWidth);
        message.setPackageLength(packageLength);

        ChatMessage saved = chatMessageRepository.save(message);
        if (saved != null) {
            message = saved;
        }
        chatRepository.updateLastMessageSentAt(chat.getChatId());

        if (recipients == null) {
            recipients = getChatMembers(chat);
        }

        eventPublisher.publishEvent(new ChatMessageRecordedEvent(
                chat.getChatId(),
                caller.getGuid(),
                request.type(),
                recipients
        ));

        MessageDTO dto = MessageDTO.from(message, caller);
        deliverMessage(chat.getChatId(), dto, recipients);
        return dto;
    }

    @Transactional
    public void handleQuoteDecision(UUID chatId, QuoteDecisionRequest request) {
        log.info("Incoming Quote Decision for Chat [{}]", chatId);
        if (request == null) {
            throw new BadRequestException(MessageCd.INVALID_REQUEST_BODY);
        }

        if (request.message() != null
                && request.message().messageGuid() != null
                && !request.message().messageGuid().isBlank()) {
            decideOfferByMessageGuid(chatId, request.message().messageGuid(), Boolean.TRUE.equals(request.accept()), null);
            return;
        }

        User caller = ServiceContext.getUserOrThrow();
        Chat chat = findChat(chatId);

        MessageDTO quoteMessage = request.message();
        if (quoteMessage == null || !MessageType.OFFER.equals(quoteMessage.type())) {
            log.warn("Quote Decision request received without Quote Message! [{}]", quoteMessage != null ? quoteMessage.type() : null);
            throw new BadRequestException(MessageCd.MESSAGE_NOT_OFFER);
        }

        if (quoteMessage.sender() != null
                && quoteMessage.sender().userGuid() != null
                && caller.getGuid().equals(quoteMessage.sender().userGuid())) {
            throw new ForbiddenException();
        }

        if (Boolean.TRUE.equals(request.accept())) {
            acceptOffer(caller, chat, quoteMessage);
        } else {
            declineOffer(caller, chat, quoteMessage);
        }
    }

    @Transactional
    public Chat decideOfferByMessageGuid(UUID chatId, String messageGuid, boolean accept, @Nullable String reason) {
        return decideOfferByMessageGuid(chatId, messageGuid, accept, reason, null, null);
    }

    @Transactional
    public Chat decideOfferByMessageGuid(
            UUID chatId,
            String messageGuid,
            boolean accept,
            @Nullable String reason,
            @Nullable String shippingQuoteGuid,
            @Nullable String addressGuid
    ) {
        User caller = ServiceContext.getUserOrThrow();
        Chat chat = findChat(chatId);

        Optional<ChatMessage> dbMsgOpt = chatMessageRepository.findByGuidAndChatId(messageGuid, chatId);
        if (dbMsgOpt.isPresent()) {
            ChatMessage msg = dbMsgOpt.get();
            if (msg.getSenderId() != null && caller.getGuid().equals(msg.getSenderId())) {
                log.warn("User [{}] attempted to decide their own offer [{}] in Chat [{}]",
                        caller.getGuid(), messageGuid, chatId);
                throw new ForbiddenException();
            }
        } else if (!caller.getGuid().equals(chat.getClientGuid())) {
            log.warn("User [{}] attempted to decide offer in Chat [{}] where client is [{}]",
                    caller.getGuid(), chatId, chat.getClientGuid());
            throw new ForbiddenException();
        }

        if (!StatusCd.ACTIVE.equals(chat.getStatus())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        Order order = orderService.findByGuid(chat.getOrderGuid())
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        OrderStatus latestOrderStatus = orderStatusService.findLatest(order);
        if (!OrderStatusCd.QUOTING.equals(latestOrderStatus.getStatusCd())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        ChatMessage dbMsg = dbMsgOpt
                .orElseThrow(() -> new NotFoundException(MessageCd.CHAT_NOT_FOUND));

        if (!MessageType.OFFER.equals(dbMsg.getType())) {
            log.warn("Message [{}] in Chat [{}] is type [{}], expected OFFER", messageGuid, chatId, dbMsg.getType());
            throw new BadRequestException(MessageCd.MESSAGE_NOT_OFFER);
        }

        try {
            new BigDecimal(dbMsg.getContent().trim());
        } catch (Exception e) {
            throw new BadRequestException(MessageCd.OFFER_NOT_NUMERIC);
        }

        User sender = userService.findUserByGuid(dbMsg.getSenderId()).orElse(caller);
        MessageDTO authoritativeQuote = MessageDTO.from(dbMsg, sender);

        if (accept) {
            acceptOffer(caller, chat, dbMsg, authoritativeQuote, order, shippingQuoteGuid, addressGuid);
        } else {
            declineOffer(caller, chat, authoritativeQuote, reason);
        }

        return chat;
    }

    @Transactional
    public void acceptOffer(User caller, Chat chat, MessageDTO quoteMessage) {
        Order order = orderService.findByGuid(chat.getOrderGuid())
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        acceptOffer(caller, chat, null, quoteMessage, order, null, null);
    }

    private void acceptOffer(
            User caller,
            Chat chat,
            @Nullable ChatMessage offerMessage,
            MessageDTO quoteMessage,
            Order order,
            @Nullable String shippingQuoteGuid,
            @Nullable String addressGuid
    ) {
        log.info("Accepting offer for Chat [{}]", chat.getChatId());
        BigDecimal offer = new BigDecimal(quoteMessage.content().trim());

        Company company = companyService.findByCompanyGuid(chat.getCompanyGuid()).orElseThrow();
        if (StatusCd.SUSPENDED.equals(company.getStatus()) || StatusCd.INACTIVE.equals(company.getStatus())
                || StatusCd.DELETED.equals(company.getStatus())) throw new ForbiddenException();

        boolean isBuyer = Objects.equals(caller.getGuid(), chat.getClientGuid());
        if (Objects.equals(caller.getCompanyId(), company.getId()) && !isBuyer) {
            Order lockedOrder = makerCompliance.lockOrder(chat.getOrderGuid());
            makerCompliance.requireQuoteReview(lockedOrder.getId(), company.getId(), caller.getId());
        }

        boolean hasPackageData = hasPackageShippingData(offerMessage);
        OrderShippingQuote selectedQuote = null;
        if (isBuyer && hasPackageData) {
            if (shippingQuoteGuid == null || shippingQuoteGuid.isBlank() || shippingQuoteRepository == null) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
            selectedQuote = resolveValidatedShippingQuote(
                    shippingQuoteGuid.trim(),
                    addressGuid,
                    caller,
                    company,
                    order,
                    offerMessage.getGuid()
            );
        }

        Long fromAddressId = resolveCompanyOriginAddressId(company);

        OrderStatus orderStatus;
        if (selectedQuote == null && !hasPackageData && fromAddressId == null) {
            orderStatus = orderService.acceptOffer(chat.getOrderGuid(), offer, company);
        } else {
            orderStatus = orderService.acceptOffer(chat.getOrderGuid(), offer, company, offerMessage, selectedQuote, fromAddressId);
        }

        if (selectedQuote != null && shippingQuoteRepository != null) {
            selectedQuote.setUsedOrderId(order.getId());
            shippingQuoteRepository.save(selectedQuote);
        }

        log.info("Order [{}] Confirmed! New Status [{}]", orderStatus.getOrderId(), orderStatus.getStatusCd());

        eventPublisher.publishEvent(new QuoteAcceptedNotificationEvent(
                chat.getChatId(),
                chat.getOrderGuid(),
                offer,
                caller.getGuid(),
                chat.getClientGuid(),
                chat.getCompanyGuid()
        ));

        String text;
        if (selectedQuote != null) {
            BigDecimal shippingPrice = selectedQuote.getQuotePrice() != null
                    ? selectedQuote.getQuotePrice()
                    : BigDecimal.ZERO;
            BigDecimal total = offer.add(shippingPrice);
            text = "Proposta aprovada por %s! Serviço R$ %.2f + Frete (%s) R$ %.2f = Total R$ %.2f. Pedido confirmado."
                    .formatted(
                            caller.getFullName(),
                            offer,
                            selectedQuote.getServiceName(),
                            shippingPrice,
                            total
                    );
        } else {
            text = "Proposta de R$ %.2f aprovada por %s! Pedido confirmado.".formatted(
                    offer,
                    caller.getFullName()
            );
        }
        sendMessage(chat, new SendMessageRequest(text, MessageType.SYSTEM), getChatMembers(chat));

        disableChatsForOrder(chat.getOrderGuid(), chat);
    }

    public static boolean hasPackageShippingData(@Nullable ChatMessage msg) {
        return msg != null
                && msg.getPackageWeight() != null && msg.getPackageWeight().signum() > 0
                && msg.getPackageHeight() != null && msg.getPackageHeight().signum() > 0
                && msg.getPackageWidth() != null && msg.getPackageWidth().signum() > 0
                && msg.getPackageLength() != null && msg.getPackageLength().signum() > 0;
    }

    private OrderShippingQuote resolveValidatedShippingQuote(
            String quoteGuid,
            @Nullable String addressGuid,
            User buyer,
            Company company,
            Order order,
            String offerMessageGuid
    ) {
        OrderShippingQuote quote = shippingQuoteRepository.findByGuidAndStatus(quoteGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));
        if (!Objects.equals(quote.getBuyerId(), buyer.getId())
                || !Objects.equals(quote.getVendorId(), company.getId())
                || quote.getExpiresAt() == null
                || !quote.getExpiresAt().isAfter(Instant.now())
                || (quote.getUsedOrderId() != null && !Objects.equals(quote.getUsedOrderId(), order.getId()))
                || (quote.getChatMessageGuid() != null && !Objects.equals(quote.getChatMessageGuid(), offerMessageGuid))) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        if (addressGuid != null && !addressGuid.isBlank() && addressRepository != null) {
            Address address = addressRepository.findByGuidAndStatus(addressGuid.trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new BadRequestException(MessageCd.INVALID_ADDRESS_GUID));
            if (!EntityType.USER.equals(address.getOwnerType()) || !Objects.equals(address.getOwnerId(), buyer.getId())) {
                throw new ForbiddenException();
            }
            if (!Objects.equals(address.getId(), quote.getDeliveryAddressId())) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
        }
        return quote;
    }

    private @Nullable Long resolveCompanyOriginAddressId(Company company) {
        if (company == null || addressRepository == null) {
            return null;
        }
        if (company.getAddressId() != null) {
            Address addr = addressRepository.findById(company.getAddressId()).orElse(null);
            if (addr != null && StatusCd.ACTIVE.equals(addr.getStatus())
                    && EntityType.COMPANY.equals(addr.getOwnerType())
                    && Objects.equals(company.getId(), addr.getOwnerId())) {
                return addr.getId();
            }
        }
        return addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.COMPANY, company.getId(), StatusCd.ACTIVE)
                .stream()
                .findFirst()
                .map(Address::getId)
                .orElse(null);
    }

    private static boolean isValidPositiveInt(@Nullable Integer value) {
        return value != null && value >= 1;
    }

    private static boolean isValidPositiveDecimal(@Nullable BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    @Transactional
    public void disableChatsForOrder(String orderGuid, Chat selected) {
        List<Chat> chats = chatRepository.findAllByOrderGuid(orderGuid);

        List<Chat> disabledChats = chats.stream()
                .filter(chat -> !chat.getChatId().equals(selected.getChatId()))
                .peek(chat -> chat.setStatus(StatusCd.INACTIVE)).toList();
        
        // TODO this can be VERY MUCH improved
        disabledChats = chatRepository.saveAll(disabledChats);
        for (Chat chat : disabledChats) {
            List<String> members = getChatMembers(chat);
            sendMessage(chat, new SendMessageRequest("Chat desabilitado", MessageType.SYSTEM), members);
            eventPublisher.publishEvent(new ChatEventToPublish(members, ChatEvent.chatDisabled(chat.getChatId())));
        }
    }

    @Transactional
    public void declineOffer(User caller, Chat chat, MessageDTO quoteMessage) {
        declineOffer(caller, chat, quoteMessage, null);
    }

    @Transactional
    public void declineOffer(User caller, Chat chat, MessageDTO quoteMessage, @Nullable String reason) {
        log.info("Rejecting offer for Chat [{}]", chat.getChatId());

        String baseText = "Oferta de R$ %.2f recusada por %s".formatted(
                new BigDecimal(quoteMessage.content().trim()),
                caller.getFullName()
        );
        String text = (reason != null && !reason.isBlank()) ? baseText + " (" + reason.trim() + ")" : baseText;

        SendMessageRequest messageRequest = new SendMessageRequest(text, MessageType.SYSTEM);

        sendMessage(chat, messageRequest, getChatMembers(chat));
    }

    public @NonNull Chat findAuthorizedChat(UUID chatId) {
        return findChat(chatId);
    }

    private @NonNull Chat findChat(UUID chatId) {
        User caller = ServiceContext.getUserOrThrow();

        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new NotFoundException(MessageCd.CHAT_NOT_FOUND));

        List<String> members = getChatMembers(chat);
        boolean isMember = members.stream().anyMatch(guid -> guid.equals(caller.getGuid()));

        if (!isMember) {
            throw new ForbiddenException();
        }

        return chat;
    }

    private List<String> getChatMembers(Chat chat) {
        return chatRepository.findAllMemberGuid(chat.getChatId());
    }

    public void deliverMessage(UUID chatId, MessageDTO messageDTO, List<String> recipients) {
        eventPublisher.publishEvent(new ChatEventToPublish(recipients, ChatEvent.messageSent(chatId, messageDTO)));
    }

}
