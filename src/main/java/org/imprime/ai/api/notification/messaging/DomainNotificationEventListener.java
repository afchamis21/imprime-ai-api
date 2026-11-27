package org.imprime.ai.api.notification.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.notification.composer.AccountNotificationComposer;
import org.imprime.ai.api.notification.composer.ChatNotificationComposer;
import org.imprime.ai.api.notification.composer.MarketplaceNotificationComposer;
import org.imprime.ai.api.notification.composer.OrderNotificationComposer;
import org.imprime.ai.api.notification.event.ChatDigestReadyEvent;
import org.imprime.ai.api.notification.event.IpNoticeReceiptNotificationEvent;
import org.imprime.ai.api.notification.event.ModerationNotificationEvent;
import org.imprime.ai.api.notification.event.OrderPurchasedNotificationEvent;
import org.imprime.ai.api.notification.event.OrderStatusChangedNotificationEvent;
import org.imprime.ai.api.notification.event.PayoutNotificationEvent;
import org.imprime.ai.api.notification.event.QuoteAcceptedNotificationEvent;
import org.imprime.ai.api.notification.event.RefundDecisionNotificationEvent;
import org.imprime.ai.api.notification.event.ReviewActivityNotificationEvent;
import org.imprime.ai.api.notification.event.SecurityEmailNotificationEvent;
import org.imprime.ai.api.notification.event.SupportTicketNotificationEvent;
import org.imprime.ai.api.notification.event.UserRegisteredNotificationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Orquestrador assíncrono que escuta eventos internos de domínio após commit
 * e publica mensagens leves na fila do RabbitMQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainNotificationEventListener {
    private final EmailEventPublisher emailEventPublisher;
    private final OrderNotificationComposer orderNotificationComposer;
    private final ChatNotificationComposer chatNotificationComposer;
    private final AccountNotificationComposer accountNotificationComposer;
    private final MarketplaceNotificationComposer marketplaceNotificationComposer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderPurchased(OrderPurchasedNotificationEvent event) {
        dispatchSafely("OrderPurchased", () -> orderNotificationComposer.buildPurchaseEmails(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQuoteAccepted(QuoteAcceptedNotificationEvent event) {
        dispatchSafely("QuoteAccepted", () -> orderNotificationComposer.buildQuoteAcceptedEmails(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderStatusChanged(OrderStatusChangedNotificationEvent event) {
        dispatchSafely("OrderStatusChanged", () -> orderNotificationComposer.buildStatusChangedEmails(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatDigestReady(ChatDigestReadyEvent event) {
        dispatchSafely("ChatDigestReady", () -> chatNotificationComposer.buildChatDigestEmail(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserRegistered(UserRegisteredNotificationEvent event) {
        dispatchSafely("UserRegistered", () -> accountNotificationComposer.buildWelcomeEmail(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSecurityEmail(SecurityEmailNotificationEvent event) {
        dispatchSafely("SecurityEmail", () -> accountNotificationComposer.buildSecurityEmail(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRefundDecision(RefundDecisionNotificationEvent event) {
        dispatchSafely("RefundDecision", () -> marketplaceNotificationComposer.buildRefundEmails(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayout(PayoutNotificationEvent event) {
        dispatchSafely("Payout", () -> marketplaceNotificationComposer.buildPayoutEmails(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSupportTicket(SupportTicketNotificationEvent event) {
        dispatchSafely("SupportTicket", () -> marketplaceNotificationComposer.buildSupportTicketEmails(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onModeration(ModerationNotificationEvent event) {
        dispatchSafely("Moderation", () -> marketplaceNotificationComposer.buildModerationEmails(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIpNoticeReceipt(IpNoticeReceiptNotificationEvent event) {
        dispatchSafely("IpNoticeReceipt", () -> marketplaceNotificationComposer.buildIpNoticeReceiptEmails(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewActivity(ReviewActivityNotificationEvent event) {
        dispatchSafely("ReviewActivity", () -> marketplaceNotificationComposer.buildReviewActivityEmails(event));
    }

    protected void dispatchSafely(String eventName, java.util.function.Supplier<List<EmailQueueMessage>> producer) {
        try {
            List<EmailQueueMessage> messages = producer.get();
            if (messages == null || messages.isEmpty()) {
                return;
            }
            for (EmailQueueMessage msg : messages) {
                emailEventPublisher.publish(msg);
            }
        } catch (Exception ex) {
            log.error("Erro ao processar notificação de domínio [{}]: {}", eventName, ex.getMessage(), ex);
        }
    }
}
