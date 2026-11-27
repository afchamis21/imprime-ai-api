package org.imprime.ai.api.notification.composer;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.ChatDigestReadyEvent;
import org.imprime.ai.api.notification.messaging.EmailCategory;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.imprime.ai.api.notification.template.NotificationFormatting;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Monta o e-mail resumo consolidado de mensagens não lidas em uma conversa.
 */
@Service
@RequiredArgsConstructor
public class ChatNotificationComposer {
    private static final int MAX_PREVIEW_MESSAGES = 3;

    private final NotificationProperties properties;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final FileAssetRepository fileAssetRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildChatDigestEmail(ChatDigestReadyEvent event) {
        if (event == null || event.chatId() == null || event.recipientUserGuid() == null) {
            return List.of();
        }
        Chat chat = chatRepository.findById(event.chatId()).orElse(null);
        if (chat == null || !StatusCd.ACTIVE.equals(chat.getStatus())) {
            return List.of();
        }

        User recipient = userRepository.findUserByGuid(event.recipientUserGuid()).orElse(null);
        if (recipient == null || !StatusCd.ACTIVE.equals(recipient.getStatus())
                || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return List.of();
        }

        Instant cutoff = event.windowCutoff() != null ? event.windowCutoff() : Instant.EPOCH;
        List<ChatMessage> unread = chatMessageRepository.findUnreadSince(chat.getChatId(), recipient.getGuid(), cutoff);
        if (unread == null || unread.isEmpty()) {
            return List.of();
        }

        boolean isRecipientBuyer = Objects.equals(recipient.getGuid(), chat.getClientGuid());
        Company maker = chat.getCompanyGuid() != null ? companyRepository.findByGuid(chat.getCompanyGuid()).orElse(null) : null;
        User buyer = chat.getClientGuid() != null ? userRepository.findUserByGuid(chat.getClientGuid()).orElse(null) : null;

        String counterpartName = isRecipientBuyer
                ? (maker != null && maker.getName() != null ? maker.getName() : "Estúdio Maker")
                : (buyer != null ? buyer.getFullName() : "Comprador");

        Order order = chat.getOrderGuid() != null ? orderRepository.findOrderByGuid(chat.getOrderGuid()).orElse(null) : null;
        String orderCode = formatOrderCode(order, chat.getOrderGuid());
        String projectTitle = resolveProjectTitle(order);

        int totalUnread = unread.size();
        boolean hasOffer = unread.stream().anyMatch(m -> MessageType.OFFER.equals(m.getType()));
        String latestOfferFormatted = unread.stream()
                .filter(m -> MessageType.OFFER.equals(m.getType()))
                .reduce((first, second) -> second)
                .map(ChatMessage::getContent)
                .map(ChatNotificationComposer::tryFormatOffer)
                .orElse(null);

        int fromIndex = Math.max(0, totalUnread - MAX_PREVIEW_MESSAGES);
        List<ChatMessage> tail = unread.subList(fromIndex, totalUnread);
        List<Map<String, String>> previews = new ArrayList<>(tail.size());
        for (ChatMessage msg : tail) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("sender", resolveSenderDisplay(msg.getSenderId(), counterpartName));
            item.put("time", NotificationFormatting.formatInstantBr(msg.getCreateDt()));
            item.put("preview", formatMessagePreview(msg));
            previews.add(item);
        }

        String subject = hasOffer
                ? "Nova proposta e " + totalUnread + (totalUnread == 1 ? " mensagem não lida" : " mensagens não lidas") + " (" + orderCode + ") — Imprime AÍ"
                : (totalUnread == 1
                ? "Nova mensagem de " + counterpartName + " (" + orderCode + ") — Imprime AÍ"
                : totalUnread + " novas mensagens de " + counterpartName + " (" + orderCode + ") — Imprime AÍ");

        String baseUrl = properties.normalizedBaseUrl();
        String chatUrl = baseUrl + "/chat/" + chat.getChatId();

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject);
        vars.put("preheader", counterpartName + " enviou " + totalUnread + (totalUnread == 1 ? " nova mensagem" : " novas mensagens") + " sobre " + orderCode + ".");
        vars.put("heading", totalUnread == 1
                ? "Você recebeu uma nova mensagem"
                : "Você tem " + totalUnread + " mensagens não lidas");
        vars.put("recipientName", recipient.getFirstName());
        vars.put("counterpartName", counterpartName);
        vars.put("orderCode", orderCode);
        vars.put("projectTitle", projectTitle);
        vars.put("unreadCount", totalUnread);
        vars.put("hasMoreMessages", totalUnread > MAX_PREVIEW_MESSAGES);
        vars.put("hiddenCount", Math.max(0, totalUnread - MAX_PREVIEW_MESSAGES));
        vars.put("hasOffer", hasOffer);
        vars.put("latestOfferFormatted", latestOfferFormatted);
        vars.put("previews", previews);
        vars.put("ctaUrl", chatUrl);

        String eventId = "chat-digest:" + chat.getChatId() + ":" + recipient.getGuid() + ":"
                + (event.digestGeneratedAt() != null ? event.digestGeneratedAt().toEpochMilli() : Instant.now().toEpochMilli());

        return List.of(new EmailQueueMessage(
                eventId,
                EmailCategory.CHAT_DIGEST,
                "chat/unread-digest",
                recipient.getEmail(),
                recipient.getFullName(),
                subject,
                vars,
                Instant.now()
        ));
    }

    private String resolveSenderDisplay(String senderGuid, String fallback) {
        if (senderGuid == null || senderGuid.isBlank()) {
            return fallback;
        }
        return userRepository.findUserByGuid(senderGuid)
                .map(User::getFirstName)
                .orElse(fallback);
    }

    private String resolveProjectTitle(Order order) {
        if (order == null || order.getFileAssetId() == null) {
            return "Impressão 3D sob demanda";
        }
        return fileAssetRepository.findById(order.getFileAssetId())
                .map(asset -> NotificationFormatting.safeText(asset.getName(), "Impressão 3D sob demanda"))
                .orElse("Impressão 3D sob demanda");
    }

    private String formatOrderCode(Order order, String fallbackGuid) {
        if (order != null && order.getOrderCode() != null && !order.getOrderCode().isBlank()) {
            return order.getOrderCode();
        }
        String guid = order != null ? order.getGuid() : fallbackGuid;
        if (guid == null || guid.isBlank()) {
            return "PED-CHAT";
        }
        String compact = guid.replace("-", "");
        String suffix = compact.length() >= 6 ? compact.substring(0, 6) : compact;
        return "PED-" + suffix.toUpperCase(Locale.ROOT);
    }

    private static String formatMessagePreview(ChatMessage msg) {
        if (msg == null) {
            return "";
        }
        if (MessageType.OFFER.equals(msg.getType())) {
            String formatted = tryFormatOffer(msg.getContent());
            return formatted != null ? "Proposta de orçamento: " + formatted : "Proposta de orçamento enviada";
        }
        if (MessageType.ATTACHMENT.equals(msg.getType())) {
            return "Anexo enviado no chat";
        }
        return NotificationFormatting.truncate(NotificationFormatting.safeText(msg.getContent(), "Mensagem"), 180);
    }

    private static String tryFormatOffer(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return NotificationFormatting.formatBrl(new BigDecimal(raw.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
