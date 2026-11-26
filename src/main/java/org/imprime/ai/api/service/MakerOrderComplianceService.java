package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.RefundRequest;
import org.imprime.ai.api.model.enums.*;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.repo.db.RefundRequestRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MakerOrderComplianceService {
    private final MakerCompanyResolver makerCompanyResolver;
    private final OrderRepository orders;
    private final OrderStatusService orderStatuses;
    private final ChatRepository chats;
    private final ChatMessageRepository chatMessages;
    private final PrintQueueItemRepository jobs;
    private final RefundRequestRepository refunds;
    private final LegalDocumentService documents;
    private final JdbcClient jdbc;

    public Order lockOrder(String orderGuid) {
        return orders.findLockedByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
    }

    public void requireNoRefusal(long orderId, long companyId) {
        if (hasRefusal(orderId, companyId)) throw new ConflictException(MessageCd.CONFLICT);
    }

    public void auditFileAccess(long orderId, long assetId, long actorId) {
        jdbc.sql("""
                INSERT INTO order_file_access (order_id, file_asset_id, actor_user_id)
                VALUES (:order, :asset, :actor)
                """).param("order", orderId).param("asset", assetId).param("actor", actorId).update();
    }

    public boolean hasRefusal(long orderId, long companyId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM maker_order_compliance
                    WHERE order_id = :order AND company_id = :company AND action = 'REFUSE')
                """).param("order", orderId).param("company", companyId).query(Boolean.class).single();
    }

    @Transactional
    public void acknowledgeQuoteReview(String orderGuid, boolean approved, String version) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Order order = lockOrder(orderGuid);
        if (!chats.existsByOrderGuidAndCompanyGuid(orderGuid, company.getGuid())) throw new ForbiddenException();
        if (!approved || order.getVendorId() != null || orderStatuses.findLatest(order).getStatusCd() != OrderStatusCd.QUOTING) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        requireNoRefusal(order.getId(), company.getId());
        long policy = documents.requireAcceptableVersion("prohibited-items", version);
        jdbc.sql("""
                INSERT INTO maker_order_compliance
                    (order_id, company_id, actor_user_id, action, legal_document_id)
                VALUES (:order, :company, :actor, 'QUOTE', :policy)
                """).param("order", order.getId()).param("company", company.getId())
                .param("actor", ServiceContext.getUserOrThrow().getId()).param("policy", policy).update();
    }

    public void requireQuoteReview(long orderId, long companyId, long actorId) {
        long policy = documents.currentAcceptableVersionId("prohibited-items");
        boolean reviewed = jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM maker_order_compliance review
                    WHERE review.order_id = :order AND review.company_id = :company
                        AND review.actor_user_id = :actor AND review.action = 'QUOTE'
                        AND review.legal_document_id = :policy)
                """).param("order", orderId).param("company", companyId).param("actor", actorId)
                .param("policy", policy).query(Boolean.class).single();
        if (!reviewed) throw new ConflictException(MessageCd.LEGAL_VERSION_UNAVAILABLE);
    }

    public void approvePrintStart(long orderId, long jobId, Company company, Boolean approved, String version) {
        Order order = orders.findLockedById(orderId)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        if (!Objects.equals(company.getId(), order.getVendorId())) throw new ForbiddenException();
        OrderStatusCd status = orderStatuses.findLatest(order).getStatusCd();
        boolean paidAndWaiting = status == OrderStatusCd.CONFIRMED && order.getPaymentConfirmedAt() != null;
        if ((!paidAndWaiting && status != OrderStatusCd.IN_PRODUCTION) || !Boolean.TRUE.equals(approved)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        requireNoRefusal(orderId, company.getId());
        String jobStatus = jdbc.sql("SELECT job_status FROM print_queue_item WHERE print_queue_item_id = :job FOR UPDATE")
                .param("job", jobId).query(String.class).optional().orElse(null);
        if (!PrintJobStatus.QUEUED.getCode().equals(jobStatus)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        long policyId = documents.requireAcceptableVersion("prohibited-items", version);
        jdbc.sql("""
                INSERT INTO maker_order_compliance
                    (order_id, company_id, actor_user_id, action, legal_document_id)
                VALUES (:order, :company, :actor, 'START', :policy)
                """).param("order", orderId).param("company", company.getId())
                .param("actor", ServiceContext.getUserOrThrow().getId()).param("policy", policyId).update();
        if (paidAndWaiting) orderStatuses.move(order, OrderStatusCd.IN_PRODUCTION);
    }

    @Transactional
    public void refuse(String orderGuid, RefusalReason reason) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Order order = lockOrder(orderGuid);
        boolean assigned = Objects.equals(order.getVendorId(), company.getId());
        boolean hadChat = chats.existsByOrderGuidAndCompanyGuid(orderGuid, company.getGuid());
        if (!assigned && !hadChat) throw new ForbiddenException();
        if (hasRefusal(order.getId(), company.getId())) return;

        OrderStatusCd status = orderStatuses.findLatest(order).getStatusCd();
        boolean canRefuseQuote = order.getVendorId() == null && status == OrderStatusCd.QUOTING;
        boolean canRefuseSale = assigned && (status == OrderStatusCd.CONFIRMED || status == OrderStatusCd.IN_PRODUCTION);
        if (!canRefuseQuote && !canRefuseSale) throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        List<PrintQueueItem> orderJobs = assigned
                ? jobs.findAllByCompanyIdAndOrderIdInAndStatus(company.getId(), List.of(order.getId()), StatusCd.ACTIVE)
                : List.of();
        if (orderJobs.stream().anyMatch(job -> job.getJobStatus() != PrintJobStatus.QUEUED)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        jdbc.sql("""
                INSERT INTO maker_order_compliance (order_id, company_id, actor_user_id, action, reason)
                VALUES (:order, :company, :actor, 'REFUSE', :reason)
                """).param("order", order.getId()).param("company", company.getId())
                .param("actor", ServiceContext.getUserOrThrow().getId()).param("reason", reason.name()).update();
        List<Chat> affectedChats = chats.findAllByOrderGuid(orderGuid).stream()
                .filter(chat -> company.getGuid().equals(chat.getCompanyGuid()))
                .toList();
        affectedChats.forEach(chat -> {
            chat.setStatus(StatusCd.INACTIVE);
            ChatMessage message = new ChatMessage();
            message.setChatId(chat.getChatId());
            message.setSenderId(ServiceContext.getUserOrThrow().getGuid());
            message.setType(MessageType.SYSTEM);
            message.setContent("O maker recusou a impressão por suspeita de infração ou item proibido.");
            chatMessages.save(message);
        });
        chats.saveAll(affectedChats);

        if (assigned) {
            order.setCancellationReason("Recusa por conformidade");
            orders.save(order);
            orderStatuses.move(order, OrderStatusCd.CANCELLED);
            orderJobs.forEach(job -> job.setStatus(StatusCd.INACTIVE));
            jobs.saveAll(orderJobs);
            if (order.getCheckoutSessionId() != null || status == OrderStatusCd.IN_PRODUCTION) {
                requestRefundReview(order);
            }
        }
    }

    private void requestRefundReview(Order order) {
        if (order.getOrderTotal() == null || order.getOrderTotal().signum() <= 0
                || !refunds.findAllByOrderIdAndStatusOrderByCreateDtDesc(order.getId(), StatusCd.ACTIVE).isEmpty()) return;
        RefundRequest refund = new RefundRequest();
        refund.setOrderId(order.getId());
        refund.setRequestedByUserId(order.getBuyerId());
        refund.setRefundType(RefundType.FULL);
        refund.setRequestedAmount(order.getOrderTotal());
        refund.setRefundStatus(RefundStatus.PENDING);
        refund.setReason("Recusa por conformidade. Verificar pagamento antes do reembolso.");
        refunds.save(refund);
    }

    public enum RefusalReason { COPYRIGHT, PROHIBITED_ITEM }
}
