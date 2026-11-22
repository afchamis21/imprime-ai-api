package org.imprime.ai.api.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.order.OrderShipmentResponse;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderShipment;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderShipmentStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderShipmentRepository;
import org.imprime.ai.api.service.OrderManagementService.OrderFileContent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderShipmentService {
    private final MelhorEnvioClient melhorEnvioClient;
    private final OrderRepository orderRepository;
    private final OrderShipmentRepository shipmentRepository;
    private final OrderStatusService orderStatusService;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderShipmentResponse generate(String orderGuid) {
        Order order = loadOrder(orderGuid);
        requireMaker(order);
        if (!OrderStatusCd.READY_TO_SHIP.equals(orderStatusService.findLatest(order).getStatusCd())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        if (order.getShippingServiceId() == null || order.getShippingQuoteSnapshotJson() == null) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        melhorEnvioClient.validateConfiguration();

        OrderShipment shipment = shipmentRepository
                .findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(order.getId(), StatusCd.ACTIVE)
                .stream().findFirst().orElseGet(() -> createShipment(order));
        if (shipment.getShipmentStatus() == OrderShipmentStatus.CART_PENDING) {
            shipment.setLastError("A criação do envio teve resposta inconclusiva. Verifique o carrinho da conta Melhor Envio antes de tentar novamente.");
            shipmentRepository.save(shipment);
            return response(order, List.of(shipment));
        }
        if (shipment.getLastError() != null && shipment.getLastError().startsWith("O preço atual")) {
            return response(order, List.of(shipment));
        }

        try {
            if (shipment.getShipmentStatus() == OrderShipmentStatus.SELECTED) {
                shipment.setShipmentStatus(OrderShipmentStatus.CART_PENDING);
                shipmentRepository.save(shipment);
                Map<String, Object> cartPayload = readCartSnapshot(order.getShippingQuoteSnapshotJson());
                cartPayload.put("service", order.getShippingServiceId());
                JsonNode created = melhorEnvioClient.post("/api/v2/me/cart", cartPayload);
                String externalId = findText(created, "id", "data.id", "order.id");
                if (externalId == null || externalId.isBlank()) {
                    shipment.setLastError("O Melhor Envio não retornou o ID do envio.");
                    shipmentRepository.save(shipment);
                    return response(order, List.of(shipment));
                }
                shipment.setExternalOrderId(externalId);
                shipment.setShipmentStatus(OrderShipmentStatus.CART_CREATED);
                shipment.setLastError(null);
                order.setExternalShipmentId(externalId);
                orderRepository.save(order);
                shipmentRepository.save(shipment);
                BigDecimal actualPrice = decimal(created, "custom_price", "price", "data.custom_price", "data.price");
                if (actualPrice != null && shipment.getQuotedPrice() != null
                        && actualPrice.compareTo(shipment.getQuotedPrice()) != 0) {
                    shipment.setLastError("O preço atual do Melhor Envio divergiu da cotação do comprador. A compra foi pausada para evitar cobrança sem aprovação.");
                    shipmentRepository.save(shipment);
                    return response(order, List.of(shipment));
                }
            }

            if (shipment.getShipmentStatus() == OrderShipmentStatus.CART_CREATED) {
                JsonNode cartDetails = melhorEnvioClient.get("/api/v2/me/cart/" + shipment.getExternalOrderId());
                BigDecimal actualPrice = decimal(cartDetails,
                        "custom_price", "price", "service.custom_price", "service.price",
                        "data.custom_price", "data.price", "data.service.custom_price", "data.service.price");
                if (actualPrice == null) {
                    shipment.setLastError("Não foi possível confirmar o preço atual da etiqueta. A compra foi pausada para conferência.");
                    shipmentRepository.save(shipment);
                    return response(order, List.of(shipment));
                }
                if (shipment.getQuotedPrice() != null && actualPrice.compareTo(shipment.getQuotedPrice()) != 0) {
                    shipment.setLastError("O preço atual do Melhor Envio divergiu da cotação do comprador. A compra foi pausada para evitar cobrança sem aprovação.");
                    shipmentRepository.save(shipment);
                    return response(order, List.of(shipment));
                }
                shipment.setLastError(null);
                shipment.setShipmentStatus(OrderShipmentStatus.CHECKOUT_PENDING);
                shipmentRepository.save(shipment);
                melhorEnvioClient.post("/api/v2/me/shipment/checkout", Map.of("orders", List.of(shipment.getExternalOrderId())));
                shipment.setShipmentStatus(OrderShipmentStatus.PURCHASED);
                shipmentRepository.save(shipment);
            } else if (shipment.getShipmentStatus() == OrderShipmentStatus.CHECKOUT_PENDING) {
                JsonNode current = melhorEnvioClient.get("/api/v2/me/cart/" + shipment.getExternalOrderId());
                if (isPurchased(current)) {
                    shipment.setShipmentStatus(OrderShipmentStatus.PURCHASED);
                    shipmentRepository.save(shipment);
                } else {
                    shipment.setLastError("O pagamento da etiqueta está pendente de reconciliação para evitar cobrança duplicada.");
                    shipmentRepository.save(shipment);
                    return response(order, List.of(shipment));
                }
            }

            if (shipment.getShipmentStatus() == OrderShipmentStatus.PURCHASED) {
                shipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
                shipmentRepository.save(shipment);
                melhorEnvioClient.post("/api/v2/me/shipment/generate", Map.of("orders", List.of(shipment.getExternalOrderId())));
                shipment.setLastError(null);
                shipmentRepository.save(shipment);
            }
            syncExternalShipment(order, shipment);
        } catch (RuntimeException exception) {
            log.warn("Melhor Envio shipment operation failed for order {} ({})", order.getGuid(), exception.getClass().getSimpleName());
            shipment.setLastError("Não foi possível concluir esta etapa com o Melhor Envio. Consulte a etiqueta no painel antes de repetir a operação.");
            shipmentRepository.save(shipment);
        }
        return response(order, List.of(shipment));
    }

    @Transactional
    public OrderShipmentResponse get(String orderGuid) {
        Order order = loadOrder(orderGuid);
        requireParticipant(order);
        List<OrderShipment> shipments = shipmentRepository
                .findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);
        for (OrderShipment shipment : shipments) syncExternalShipment(order, shipment);
        return response(order, shipments);
    }

    @Transactional
    public OrderFileContent downloadLabel(String orderGuid, String shipmentGuid) {
        Order order = loadOrder(orderGuid);
        requireMaker(order);
        OrderShipment shipment = shipmentRepository.findByGuidAndOrderIdAndStatus(
                        shipmentGuid, order.getId(), StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        syncExternalShipment(order, shipment);
        if (shipment.getExternalOrderId() == null
                || (shipment.getShipmentStatus() != OrderShipmentStatus.LABEL_READY
                && shipment.getShipmentStatus() != OrderShipmentStatus.GENERATED)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        JsonNode print = melhorEnvioClient.post("/api/v2/me/shipment/print",
                Map.of("mode", "public", "orders", List.of(shipment.getExternalOrderId())));
        String labelUrl = findText(print, "url", "data.url", "orders.0.url", "0.url", "link", "data.link");
        if (labelUrl == null || labelUrl.isBlank()) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        byte[] bytes = melhorEnvioClient.download(labelUrl);
        if (bytes == null || bytes.length == 0) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        return new OrderFileContent("etiqueta-" + order.getOrderCode() + ".pdf", "application/pdf", bytes);
    }

    private OrderShipment createShipment(Order order) {
        JsonNode snapshot = readSnapshot(order.getShippingQuoteSnapshotJson());
        OrderShipment shipment = new OrderShipment();
        shipment.setOrderId(order.getId());
        shipment.setServiceId(order.getShippingServiceId());
        shipment.setServiceName(order.getShippingServiceName());
        shipment.setCarrierName(text(snapshot.path("quote").path("company").path("name")));
        shipment.setQuotedPrice(order.getShippingQuotePrice());
        shipment.setPackageSnapshotJson(snapshot.path("cart").toString());
        shipment.setShipmentStatus(OrderShipmentStatus.SELECTED);
        return shipmentRepository.save(shipment);
    }

    private void syncExternalShipment(Order order, OrderShipment shipment) {
        if (shipment.getExternalOrderId() == null) return;
        boolean shouldPoll = shipment.getUpdateDt() == null || shipment.getUpdateDt().isBefore(Instant.now().minus(30, ChronoUnit.SECONDS));
        if (!shouldPoll) return;
        try {
            JsonNode status = melhorEnvioClient.get("/api/v2/me/cart/" + shipment.getExternalOrderId());
            String tracking = findText(status, "tracking", "tracking_code", "trackingCode", "data.tracking", "data.tracking_code", "tracking.code", "data.tracking.code");
            String trackingUrl = findText(status, "tracking_url", "trackingUrl", "data.tracking_url", "data.url");
            String externalStatus = findText(status, "status", "data.status");
            boolean generated = findText(status, "generated_at", "data.generated_at") != null
                    || "generated".equalsIgnoreCase(externalStatus) || "ready-to-print".equalsIgnoreCase(externalStatus);
            if (generated && shipment.getShipmentStatus() != OrderShipmentStatus.LABEL_READY) {
                shipment.setShipmentStatus(OrderShipmentStatus.LABEL_READY);
                shipment.setLabelExpiresAt(Instant.now().plus(20, ChronoUnit.DAYS));
            }
            if (tracking != null && !tracking.isBlank()) {
                shipment.setTrackingCode(tracking);
                order.setTrackingCode(tracking);
                order.setCarrierName(shipment.getCarrierName());
                order.setTrackingStatus(externalStatus);
                order.setTrackingUrl(trackingUrl == null ? "https://www.melhorrastreio.com.br/" : trackingUrl);
                orderRepository.save(order);
                if (OrderStatusCd.READY_TO_SHIP.equals(orderStatusService.findLatest(order).getStatusCd())) {
                    orderStatusService.move(order, OrderStatusCd.IN_TRANSIT);
                }
            }
            if (shipment.getShipmentStatus() == OrderShipmentStatus.LABEL_READY
                    && (shipment.getTrackingCode() == null || shipment.getTrackingCode().isBlank())) {
                JsonNode trackingResponse = melhorEnvioClient.post("/api/v2/me/shipment/tracking",
                        Map.of("orders", List.of(shipment.getExternalOrderId())));
                JsonNode row = trackingResponse != null && trackingResponse.isArray() && !trackingResponse.isEmpty()
                        ? trackingResponse.get(0) : trackingResponse == null ? null : trackingResponse.path("data").path(0);
                tracking = findText(row, "tracking", "tracking_code", "code", "trackingCode", "tracking.code");
                if (tracking != null && !tracking.isBlank()) {
                    shipment.setTrackingCode(tracking);
                    order.setTrackingCode(tracking);
                    order.setCarrierName(shipment.getCarrierName());
                    order.setTrackingStatus(findText(row, "status", "tracking_status"));
                    trackingUrl = findText(row, "url", "tracking_url", "trackingUrl");
                    order.setTrackingUrl(trackingUrl == null ? "https://www.melhorrastreio.com.br/" : trackingUrl);
                    orderRepository.save(order);
                    if (OrderStatusCd.READY_TO_SHIP.equals(orderStatusService.findLatest(order).getStatusCd())) {
                        orderStatusService.move(order, OrderStatusCd.IN_TRANSIT);
                    }
                }
            }
            shipmentRepository.save(shipment);
        } catch (RuntimeException exception) {
            log.debug("Could not sync Melhor Envio order {} ({})", shipment.getExternalOrderId(), exception.getClass().getSimpleName());
        }
    }

    private boolean isPurchased(JsonNode current) {
        String status = findText(current, "status", "data.status", "purchase.status", "data.purchase.status");
        return status != null && List.of("paid", "released", "purchased", "approved").contains(status.toLowerCase());
    }

    private Map<String, Object> readCartSnapshot(String snapshotJson) {
        JsonNode snapshot = readSnapshot(snapshotJson);
        JsonNode cart = snapshot.path("cart");
        if (!cart.isObject()) throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        return objectMapper.convertValue(cart, LinkedHashMap.class);
    }

    private JsonNode readSnapshot(String snapshotJson) {
        try { return objectMapper.readTree(snapshotJson); }
        catch (Exception exception) { throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION); }
    }

    private String findText(JsonNode node, String... paths) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String path : paths) {
            JsonNode value = node.at("/" + path.replace('.', '/'));
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private BigDecimal decimal(JsonNode node, String... paths) {
        String value = findText(node, paths);
        try { return value == null ? null : new BigDecimal(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private OrderShipmentResponse response(Order order, List<OrderShipment> shipments) {
        return new OrderShipmentResponse(order.getCarrierName(), order.getShippingServiceName(), order.getTrackingCode(),
                order.getTrackingStatus(), order.getTrackingUrl(), shipments.stream().map(shipment ->
                new OrderShipmentResponse.ShipmentPackageResponse(shipment.getGuid(),
                        shipment.getShipmentStatus() == null ? null : shipment.getShipmentStatus().name(),
                        shipment.getPackageNumber(), shipment.getLabelExpiresAt(), shipment.getLastError()))
                .toList());
    }

    private Order loadOrder(String orderGuid) {
        return orderRepository.findOrderByGuid(orderGuid).orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
    }

    private void requireMaker(Order order) {
        User caller = ServiceContext.getUserOrThrow();
        if (caller.getCompanyId() == null || !caller.getCompanyId().equals(order.getVendorId())) throw new ForbiddenException();
    }

    private void requireParticipant(Order order) {
        User caller = ServiceContext.getUserOrThrow();
        if (!caller.getId().equals(order.getBuyerId())
                && (caller.getCompanyId() == null || !caller.getCompanyId().equals(order.getVendorId()))) throw new ForbiddenException();
    }
}
