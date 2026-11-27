package org.imprime.ai.api.notification.composer;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.OrderPurchasedNotificationEvent;
import org.imprime.ai.api.notification.event.OrderStatusChangedNotificationEvent;
import org.imprime.ai.api.notification.event.QuoteAcceptedNotificationEvent;
import org.imprime.ai.api.notification.messaging.EmailCategory;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.imprime.ai.api.notification.template.NotificationFormatting;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Monta os payloads de e-mail para compras, aprovações de orçamento e transições de status de pedidos.
 */
@Service
@RequiredArgsConstructor
public class OrderNotificationComposer {
    private final NotificationProperties properties;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderFileAssetRepository orderFileAssetRepository;
    private final FileAssetRepository fileAssetRepository;
    private final Model3DRepository model3DRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AddressRepository addressRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildPurchaseEmails(OrderPurchasedNotificationEvent event) {
        if (event == null || event.orderGuid() == null) {
            return List.of();
        }
        Optional<Order> orderOpt = orderRepository.findOrderByGuid(event.orderGuid());
        if (orderOpt.isEmpty()) {
            return List.of();
        }
        Order order = orderOpt.get();
        String orderCode = resolveOrderCode(order);
        User buyer = order.getBuyerId() != null ? userRepository.findById(order.getBuyerId()).orElse(null) : null;
        Company vendor = order.getVendorId() != null ? companyRepository.findById(order.getVendorId()).orElse(null) : null;

        List<OrderItem> persistedItems = order.getId() != null
                ? orderItemRepository.findAllByOrderIdAndStatusOrderByIdAsc(order.getId(), StatusCd.ACTIVE)
                : List.of();
        List<OrderFileAsset> plates = order.getId() != null
                ? orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE)
                : List.of();

        BigDecimal shipping = nonNegative(order.getShippingCost());
        BigDecimal total = nonNegative(order.getOrderTotal());
        BigDecimal subtotal = computeSubtotal(persistedItems, total, shipping);
        List<Map<String, String>> summaryItems = buildSummaryItems(order, persistedItems, plates, subtotal);
        String addressSummary = event.allDigital() ? null : formatDeliveryAddress(order.getToAddressId());

        List<EmailQueueMessage> messages = new ArrayList<>();
        String baseUrl = properties.normalizedBaseUrl();

        if (isValidActiveUser(buyer)) {
            List<Map<String, String>> infoRows = new ArrayList<>();
            infoRows.add(row("Pedido", orderCode));
            if (vendor != null && vendor.getName() != null && !event.allDigital()) {
                infoRows.add(row("Estúdio Maker", vendor.getName()));
            }
            infoRows.add(row("Data", NotificationFormatting.formatInstantBr(Instant.now())));
            infoRows.add(row("Status", event.allDigital() ? "Liberado para download" : "Pagamento confirmado"));

            String ctaUrl = event.allDigital()
                    ? baseUrl + "/profile?tab=models"
                    : baseUrl + "/profile?tab=purchases&orderGuid=" + order.getGuid();
            String ctaLabel = event.allDigital() ? "Acessar meus modelos 3D" : "Acompanhar pedido";
            String subject = "Pedido " + orderCode + " confirmado — Imprime AÍ";

            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("subject", subject);
            vars.put("preheader", "Seu pagamento foi confirmado para o pedido " + orderCode + ".");
            vars.put("heading", event.allDigital() ? "Seus modelos 3D estão prontos!" : "Compra confirmada!");
            vars.put("recipientName", buyer.getFirstName());
            vars.put("orderCode", orderCode);
            vars.put("sellerName", vendor != null ? vendor.getName() : null);
            vars.put("allDigital", event.allDigital());
            vars.put("infoRows", infoRows);
            vars.put("items", summaryItems);
            vars.put("subtotalFormatted", NotificationFormatting.formatBrl(subtotal));
            vars.put("shippingFormatted", event.allDigital() ? null : NotificationFormatting.formatBrl(shipping));
            vars.put("totalFormatted", NotificationFormatting.formatBrl(total));
            vars.put("shippingAddressSummary", addressSummary);
            vars.put("ctaLabel", ctaLabel);
            vars.put("ctaUrl", ctaUrl);

            messages.add(new EmailQueueMessage(
                    "order-purchased:" + order.getGuid() + ":buyer",
                    EmailCategory.TRANSACTIONAL_ORDER,
                    "order/purchase-confirmed-buyer",
                    buyer.getEmail(),
                    buyer.getFullName(),
                    subject,
                    vars,
                    Instant.now()
            ));
        }

        Set<Long> sellerUserIds = resolveSellerRecipientIds(vendor, persistedItems);
        for (Long sellerUserId : sellerUserIds) {
            User seller = userRepository.findById(sellerUserId).orElse(null);
            if (!isValidActiveUser(seller)) {
                continue;
            }
            List<Map<String, String>> sellerRows = new ArrayList<>();
            sellerRows.add(row("Pedido", orderCode));
            sellerRows.add(row("Comprador", buyer != null ? buyer.getFullName() : "Cliente Imprime AÍ"));
            if (order.getShippingServiceName() != null && !order.getShippingServiceName().isBlank()) {
                sellerRows.add(row("Modalidade de frete", order.getShippingServiceName()));
            }
            sellerRows.add(row("Total do pedido", NotificationFormatting.formatBrl(total)));

            String subject = "Nova venda confirmada: " + orderCode + " — Imprime AÍ";
            String ctaUrl = event.allDigital()
                    ? baseUrl + "/profile"
                    : baseUrl + "/sales-history?order=" + order.getGuid();
            String ctaLabel = event.allDigital() ? "Abrir minha conta" : "Ver venda no histórico";

            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("subject", subject);
            vars.put("preheader", "Você recebeu uma nova venda confirmada (" + orderCode + ").");
            vars.put("heading", "Você realizou uma nova venda!");
            vars.put("recipientName", vendor != null && Objects.equals(vendor.getOwnerId(), seller.getId())
                    ? vendor.getName()
                    : seller.getFirstName());
            vars.put("buyerName", buyer != null ? buyer.getFullName() : "Comprador");
            vars.put("orderCode", orderCode);
            vars.put("digitalSale", event.allDigital());
            vars.put("infoRows", sellerRows);
            vars.put("items", summaryItems);
            vars.put("subtotalFormatted", NotificationFormatting.formatBrl(subtotal));
            vars.put("shippingFormatted", event.allDigital() ? null : NotificationFormatting.formatBrl(shipping));
            vars.put("totalFormatted", NotificationFormatting.formatBrl(total));
            vars.put("ctaLabel", ctaLabel);
            vars.put("ctaUrl", ctaUrl);

            messages.add(new EmailQueueMessage(
                    "order-purchased:" + order.getGuid() + ":seller:" + seller.getId(),
                    EmailCategory.TRANSACTIONAL_ORDER,
                    "order/new-sale-seller",
                    seller.getEmail(),
                    seller.getFullName(),
                    subject,
                    vars,
                    Instant.now()
            ));
        }

        return messages;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildQuoteAcceptedEmails(QuoteAcceptedNotificationEvent event) {
        if (event == null || event.orderGuid() == null) {
            return List.of();
        }
        Order order = orderRepository.findOrderByGuid(event.orderGuid()).orElse(null);
        String orderCode = order != null ? resolveOrderCode(order) : formatOrderCodeFromGuid(event.orderGuid());
        User buyer = event.clientGuid() != null ? userRepository.findUserByGuid(event.clientGuid()).orElse(null) : null;
        Company maker = event.companyGuid() != null ? companyRepository.findByGuid(event.companyGuid()).orElse(null) : null;
        User makerOwner = resolveCompanyOwner(maker);

        String baseUrl = properties.normalizedBaseUrl();
        String chatUrl = baseUrl + "/chat/" + event.chatId();
        String offerFormatted = NotificationFormatting.formatBrl(event.offerAmount());

        List<EmailQueueMessage> messages = new ArrayList<>();

        if (isValidActiveUser(buyer)) {
            List<Map<String, String>> rows = List.of(
                    row("Pedido", orderCode),
                    row("Estúdio Maker", maker != null ? maker.getName() : "Maker"),
                    row("Valor aprovado", offerFormatted)
            );
            String subject = "Orçamento aprovado para o pedido " + orderCode + " — Imprime AÍ";
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("subject", subject);
            vars.put("preheader", "Proposta de " + offerFormatted + " aprovada. Conclua o pagamento no chat.");
            vars.put("heading", "Orçamento aprovado! Falta só o pagamento");
            vars.put("recipientName", buyer.getFirstName());
            vars.put("orderCode", orderCode);
            vars.put("makerName", maker != null ? maker.getName() : "Maker");
            vars.put("offerFormatted", offerFormatted);
            vars.put("infoRows", rows);
            vars.put("ctaUrl", chatUrl);

            messages.add(new EmailQueueMessage(
                    "quote-accepted:" + event.orderGuid() + ":buyer",
                    EmailCategory.TRANSACTIONAL_ORDER,
                    "order/quote-accepted-buyer",
                    buyer.getEmail(),
                    buyer.getFullName(),
                    subject,
                    vars,
                    Instant.now()
            ));
        }

        if (isValidActiveUser(makerOwner)) {
            List<Map<String, String>> rows = List.of(
                    row("Pedido", orderCode),
                    row("Comprador", buyer != null ? buyer.getFullName() : "Cliente"),
                    row("Valor aprovado", offerFormatted)
            );
            String subject = "Sua proposta foi aceita no pedido " + orderCode + " — Imprime AÍ";
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("subject", subject);
            vars.put("preheader", "Proposta de " + offerFormatted + " aprovada no pedido " + orderCode + ".");
            vars.put("heading", "Proposta aceita! Aguardando pagamento");
            vars.put("recipientName", maker != null ? maker.getName() : makerOwner.getFirstName());
            vars.put("buyerName", buyer != null ? buyer.getFullName() : "Comprador");
            vars.put("orderCode", orderCode);
            vars.put("offerFormatted", offerFormatted);
            vars.put("infoRows", rows);
            vars.put("ctaUrl", chatUrl);

            messages.add(new EmailQueueMessage(
                    "quote-accepted:" + event.orderGuid() + ":maker",
                    EmailCategory.TRANSACTIONAL_ORDER,
                    "order/quote-accepted-maker",
                    makerOwner.getEmail(),
                    makerOwner.getFullName(),
                    subject,
                    vars,
                    Instant.now()
            ));
        }

        return messages;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildStatusChangedEmails(OrderStatusChangedNotificationEvent event) {
        if (event == null || event.orderGuid() == null || event.newStatus() == null) {
            return List.of();
        }
        Order order = orderRepository.findOrderByGuid(event.orderGuid()).orElse(null);
        if (order == null) {
            return List.of();
        }
        String orderCode = resolveOrderCode(order);
        User buyer = order.getBuyerId() != null ? userRepository.findById(order.getBuyerId()).orElse(null) : null;
        Company maker = order.getVendorId() != null ? companyRepository.findById(order.getVendorId()).orElse(null) : null;
        User makerOwner = resolveCompanyOwner(maker);

        String baseUrl = properties.normalizedBaseUrl();
        String buyerOrderUrl = baseUrl + "/profile?tab=purchases&orderGuid=" + order.getGuid();
        String statusKey = event.orderStatusId() != null ? String.valueOf(event.orderStatusId()) : UUID.randomUUID().toString();
        String idPrefix = "order-status:" + order.getGuid() + ":" + event.newStatus().name() + ":" + statusKey;

        List<EmailQueueMessage> messages = new ArrayList<>();

        switch (event.newStatus()) {
            case IN_PRODUCTION -> {
                if (isValidActiveUser(buyer)) {
                    String subject = "Produção iniciada no pedido " + orderCode + " — Imprime AÍ";
                    Map<String, Object> vars = baseStatusVars(
                            subject,
                            "O estúdio iniciou a impressão 3D do pedido " + orderCode + ".",
                            "Seu pedido entrou em produção!",
                            buyer.getFirstName(),
                            orderCode,
                            maker != null ? maker.getName() : "Maker",
                            List.of(
                                    row("Pedido", orderCode),
                                    row("Estúdio Maker", maker != null ? maker.getName() : "Imprime AÍ"),
                                    row("Status atual", "Em produção")
                            ),
                            buyerOrderUrl
                    );
                    messages.add(new EmailQueueMessage(
                            idPrefix + ":buyer",
                            EmailCategory.TRANSACTIONAL_ORDER,
                            "order/status-in-production",
                            buyer.getEmail(),
                            buyer.getFullName(),
                            subject,
                            vars,
                            Instant.now()
                    ));
                }
            }
            case READY_TO_SHIP -> {
                if (isValidActiveUser(buyer)) {
                    String subject = "Pedido " + orderCode + " pronto para envio — Imprime AÍ";
                    Map<String, Object> vars = baseStatusVars(
                            subject,
                            "Impressão concluída! O pedido " + orderCode + " está pronto para postagem.",
                            "Peça finalizada e pronta para envio",
                            buyer.getFirstName(),
                            orderCode,
                            maker != null ? maker.getName() : "Maker",
                            List.of(
                                    row("Pedido", orderCode),
                                    row("Estúdio Maker", maker != null ? maker.getName() : "Imprime AÍ"),
                                    row("Status atual", "Pronto para envio")
                            ),
                            buyerOrderUrl
                    );
                    messages.add(new EmailQueueMessage(
                            idPrefix + ":buyer",
                            EmailCategory.TRANSACTIONAL_ORDER,
                            "order/status-ready-to-ship",
                            buyer.getEmail(),
                            buyer.getFullName(),
                            subject,
                            vars,
                            Instant.now()
                    ));
                }
            }
            case IN_TRANSIT -> {
                if (isValidActiveUser(buyer)) {
                    String subject = "Pedido " + orderCode + " enviado! Código de rastreio disponível — Imprime AÍ";
                    List<Map<String, String>> rows = new ArrayList<>();
                    rows.add(row("Pedido", orderCode));
                    if (order.getCarrierName() != null && !order.getCarrierName().isBlank()) {
                        rows.add(row("Transportadora", order.getCarrierName()));
                    }
                    if (order.getTrackingCode() != null && !order.getTrackingCode().isBlank()) {
                        rows.add(row("Código de rastreio", order.getTrackingCode()));
                    }
                    rows.add(row("Status atual", "Em trânsito"));

                    boolean hasTrackingUrl = order.getTrackingUrl() != null && order.getTrackingUrl().startsWith("http");
                    String ctaUrl = hasTrackingUrl ? order.getTrackingUrl() : buyerOrderUrl;
                    String ctaLabel = hasTrackingUrl ? "Rastrear entrega" : "Acompanhar pedido";

                    Map<String, Object> vars = baseStatusVars(
                            subject,
                            "Seu pedido " + orderCode + " foi enviado e já está em trânsito.",
                            "Seu pedido está a caminho!",
                            buyer.getFirstName(),
                            orderCode,
                            maker != null ? maker.getName() : "Maker",
                            rows,
                            ctaUrl
                    );
                    vars.put("ctaLabel", ctaLabel);

                    messages.add(new EmailQueueMessage(
                            idPrefix + ":buyer",
                            EmailCategory.TRANSACTIONAL_ORDER,
                            "order/status-shipped",
                            buyer.getEmail(),
                            buyer.getFullName(),
                            subject,
                            vars,
                            Instant.now()
                    ));
                }
            }
            case COMPLETED -> {
                if (OrderStatusCd.CONFIRMED.equals(event.previousStatus())) {
                    List<OrderItem> orderItems = order.getId() != null
                            ? orderItemRepository.findAllByOrderIdAndStatusOrderByIdAsc(order.getId(), StatusCd.ACTIVE)
                            : List.of();
                    boolean allDigital = !orderItems.isEmpty() && orderItems.stream()
                            .allMatch(item -> item.getModel3dId() != null && item.getProductId() == null);
                    if (allDigital) {
                        return List.of();
                    }
                }
                if (isValidActiveUser(buyer)) {
                    String subject = "Pedido " + orderCode + " entregue! Avalie sua experiência — Imprime AÍ";
                    Map<String, Object> vars = baseStatusVars(
                            subject,
                            "O pedido " + orderCode + " foi concluído como entregue.",
                            "Pedido entregue com sucesso!",
                            buyer.getFirstName(),
                            orderCode,
                            maker != null ? maker.getName() : "Maker",
                            List.of(
                                    row("Pedido", orderCode),
                                    row("Estúdio Maker", maker != null ? maker.getName() : "Imprime AÍ"),
                                    row("Status atual", "Entregue")
                            ),
                            buyerOrderUrl
                    );
                    messages.add(new EmailQueueMessage(
                            idPrefix + ":buyer",
                            EmailCategory.TRANSACTIONAL_ORDER,
                            "order/status-delivered",
                            buyer.getEmail(),
                            buyer.getFullName(),
                            subject,
                            vars,
                            Instant.now()
                    ));
                }
                if (isValidActiveUser(makerOwner)) {
                    String subject = "Entrega confirmada para o pedido " + orderCode + " — Imprime AÍ";
                    Map<String, Object> vars = baseStatusVars(
                            subject,
                            "O pedido " + orderCode + " foi entregue ao comprador.",
                            "Entrega confirmada e saldo liberado",
                            maker != null ? maker.getName() : makerOwner.getFirstName(),
                            orderCode,
                            maker != null ? maker.getName() : "Maker",
                            List.of(
                                    row("Pedido", orderCode),
                                    row("Valor total", NotificationFormatting.formatBrl(nonNegative(order.getOrderTotal()))),
                                    row("Status atual", "Concluído")
                            ),
                            baseUrl + "/dashboard?tab=finances"
                    );
                    messages.add(new EmailQueueMessage(
                            idPrefix + ":seller",
                            EmailCategory.TRANSACTIONAL_ORDER,
                            "order/status-delivered-seller",
                            makerOwner.getEmail(),
                            makerOwner.getFullName(),
                            subject,
                            vars,
                            Instant.now()
                    ));
                }
            }
            case CANCELLED -> {
                String reason = NotificationFormatting.safeText(
                        event.cancellationReason() != null ? event.cancellationReason() : order.getCancellationReason(),
                        null
                );
                if (isValidActiveUser(buyer)) {
                    String subject = "Pedido " + orderCode + " cancelado — Imprime AÍ";
                    Map<String, Object> vars = baseStatusVars(
                            subject,
                            "O pedido " + orderCode + " foi cancelado.",
                            "Pedido cancelado",
                            buyer.getFirstName(),
                            orderCode,
                            maker != null ? maker.getName() : "Maker",
                            List.of(
                                    row("Pedido", orderCode),
                                    row("Status atual", "Cancelado")
                            ),
                            buyerOrderUrl
                    );
                    vars.put("cancellationReason", reason);
                    messages.add(new EmailQueueMessage(
                            idPrefix + ":buyer",
                            EmailCategory.TRANSACTIONAL_ORDER,
                            "order/status-cancelled",
                            buyer.getEmail(),
                            buyer.getFullName(),
                            subject,
                            vars,
                            Instant.now()
                    ));
                }
                if (isValidActiveUser(makerOwner)) {
                    String subject = "Pedido " + orderCode + " cancelado — Imprime AÍ";
                    Map<String, Object> vars = baseStatusVars(
                            subject,
                            "O pedido " + orderCode + " foi cancelado.",
                            "Pedido cancelado",
                            maker != null ? maker.getName() : makerOwner.getFirstName(),
                            orderCode,
                            maker != null ? maker.getName() : "Maker",
                            List.of(
                                    row("Pedido", orderCode),
                                    row("Comprador", buyer != null ? buyer.getFullName() : "Cliente"),
                                    row("Status atual", "Cancelado")
                            ),
                            baseUrl + "/sales-history?order=" + order.getGuid()
                    );
                    vars.put("cancellationReason", reason);
                    messages.add(new EmailQueueMessage(
                            idPrefix + ":seller",
                            EmailCategory.TRANSACTIONAL_ORDER,
                            "order/status-cancelled",
                            makerOwner.getEmail(),
                            makerOwner.getFullName(),
                            subject,
                            vars,
                            Instant.now()
                    ));
                }
            }
            default -> {
                // CREATED, QUOTING, CONFIRMED, REFUNDED e ERROR possuem fluxos dedicados ou não notificam.
            }
        }

        return messages;
    }

    private Map<String, Object> baseStatusVars(
            String subject,
            String preheader,
            String heading,
            String recipientName,
            String orderCode,
            String makerName,
            List<Map<String, String>> infoRows,
            String ctaUrl) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject);
        vars.put("preheader", preheader);
        vars.put("heading", heading);
        vars.put("recipientName", recipientName);
        vars.put("orderCode", orderCode);
        vars.put("makerName", makerName);
        vars.put("infoRows", infoRows);
        vars.put("ctaUrl", ctaUrl);
        return vars;
    }

    private Set<Long> resolveSellerRecipientIds(Company vendor, List<OrderItem> persistedItems) {
        Set<Long> ids = new HashSet<>();
        if (vendor != null) {
            if (vendor.getOwnerId() != null) {
                ids.add(vendor.getOwnerId());
            } else {
                userRepository.findAllByCompanyId(vendor.getId()).stream()
                        .filter(this::isValidActiveUser)
                        .map(User::getId)
                        .findFirst()
                        .ifPresent(ids::add);
            }
        }
        Set<Long> modelIds = persistedItems.stream()
                .map(OrderItem::getModel3dId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!modelIds.isEmpty()) {
            model3DRepository.findAllByIdIn(modelIds).stream()
                    .map(Model3D::getAuthorUserId)
                    .filter(Objects::nonNull)
                    .forEach(ids::add);
        }
        return ids;
    }

    private User resolveCompanyOwner(Company company) {
        if (company == null) {
            return null;
        }
        if (company.getOwnerId() != null) {
            Optional<User> owner = userRepository.findById(company.getOwnerId());
            if (owner.isPresent()) {
                return owner.get();
            }
        }
        return userRepository.findAllByCompanyId(company.getId()).stream()
                .filter(this::isValidActiveUser)
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, String>> buildSummaryItems(
            Order order,
            List<OrderItem> persistedItems,
            List<OrderFileAsset> plates,
            BigDecimal subtotal) {
        List<Map<String, String>> items = new ArrayList<>();
        if (!persistedItems.isEmpty()) {
            for (OrderItem item : persistedItems) {
                int qty = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
                StringBuilder details = new StringBuilder("Qtd: ").append(qty);
                if (item.getMaterial() != null) {
                    details.append(" • ").append(item.getMaterial().name());
                }
                if (item.getColorName() != null && !item.getColorName().isBlank()) {
                    details.append(" • ").append(item.getColorName());
                }
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", NotificationFormatting.safeText(item.getItemName(), "Item 3D"));
                row.put("details", details.toString());
                row.put("totalFormatted", NotificationFormatting.formatBrl(nonNegative(item.getLineTotal())));
                items.add(row);
            }
            return items;
        }

        if (!plates.isEmpty()) {
            for (int i = 0; i < plates.size(); i++) {
                OrderFileAsset plate = plates.get(i);
                int qty = plate.getQuantity() != null && plate.getQuantity() > 0 ? plate.getQuantity() : 1;
                StringBuilder details = new StringBuilder("Mesa ").append(plate.getPlateNumber() != null ? plate.getPlateNumber() : (i + 1))
                        .append(" • Qtd: ").append(qty);
                if (plate.getMaterial() != null && !plate.getMaterial().isBlank()) {
                    details.append(" • ").append(plate.getMaterial());
                }
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", NotificationFormatting.safeText(plate.getPlateName(), "Impressão 3D sob demanda"));
                row.put("details", details.toString());
                row.put("totalFormatted", i == 0 && plates.size() == 1
                        ? NotificationFormatting.formatBrl(subtotal)
                        : (plate.getEstimatedPriceBrl() != null ? NotificationFormatting.formatBrl(plate.getEstimatedPriceBrl()) : "Incluído"));
                items.add(row);
            }
            return items;
        }

        String assetName = order.getFileAssetId() != null
                ? fileAssetRepository.findById(order.getFileAssetId()).map(FileAsset::getName).orElse("Impressão 3D sob demanda")
                : "Impressão 3D sob demanda";
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("name", assetName);
        fallback.put("details", "Qtd: 1");
        fallback.put("totalFormatted", NotificationFormatting.formatBrl(subtotal));
        items.add(fallback);
        return items;
    }

    private BigDecimal computeSubtotal(List<OrderItem> persistedItems, BigDecimal total, BigDecimal shipping) {
        if (persistedItems != null && !persistedItems.isEmpty()) {
            return persistedItems.stream()
                    .map(OrderItem::getLineTotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return total.compareTo(shipping) >= 0 ? total.subtract(shipping) : total;
    }

    private String formatDeliveryAddress(Long addressId) {
        if (addressId == null) {
            return null;
        }
        return addressRepository.findById(addressId)
                .map(addr -> String.format("%s, %s — %s/%s (CEP %s)",
                        NotificationFormatting.safeText(addr.getAddressLine1(), ""),
                        NotificationFormatting.safeText(addr.getNeighborhood(), ""),
                        NotificationFormatting.safeText(addr.getCity(), ""),
                        NotificationFormatting.safeText(addr.getState(), ""),
                        NotificationFormatting.safeText(addr.getZipCode(), "")))
                .orElse(null);
    }

    private String resolveOrderCode(Order order) {
        if (order.getOrderCode() != null && !order.getOrderCode().isBlank()) {
            return order.getOrderCode();
        }
        return formatOrderCodeFromGuid(order.getGuid());
    }

    private String formatOrderCodeFromGuid(String guid) {
        if (guid == null || guid.isBlank()) {
            return "PED-000000";
        }
        String compact = guid.replace("-", "");
        String suffix = compact.length() >= 6 ? compact.substring(0, 6) : compact;
        return "PED-" + suffix.toUpperCase(Locale.ROOT);
    }

    private boolean isValidActiveUser(User user) {
        return user != null
                && StatusCd.ACTIVE.equals(user.getStatus())
                && user.getEmail() != null
                && !user.getEmail().isBlank();
    }

    private static Map<String, String> row(String label, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("value", value != null ? value : "-");
        return map;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ZERO;
    }
}
