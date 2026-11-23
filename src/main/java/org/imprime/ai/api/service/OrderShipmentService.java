package org.imprime.ai.api.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.order.OrderShipmentResponse;
import org.imprime.ai.api.model.Address;
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
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderShipmentRepository;
import org.imprime.ai.api.service.OrderManagementService.OrderFileContent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderShipmentService {
    private final MelhorEnvioClient melhorEnvioClient;
    private final OrderRepository orderRepository;
    private final OrderShipmentRepository shipmentRepository;
    private final OrderStatusService orderStatusService;
    private final AddressRepository addressRepository;
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

        Map<String, Object> lastCartPayload = null;
        try {
            if (shipment.getShipmentStatus() == OrderShipmentStatus.SELECTED) {
                JsonNode snapshot = readSnapshot(order.getShippingQuoteSnapshotJson());
                JsonNode quoteNode = snapshot.path("quote");
                Map<String, Object> cartPayload = readCartSnapshot(order, snapshot);
                cartPayload.put("service", order.getShippingServiceId());

                String dupDocError = validateDistinctDocuments(cartPayload);
                if (dupDocError != null) {
                    shipment.setShipmentStatus(OrderShipmentStatus.SELECTED);
                    shipment.setLastError(dupDocError);
                    shipmentRepository.save(shipment);
                    return response(order, List.of(shipment));
                }

                resolveAgencyIfRequired(cartPayload, quoteNode, order);
                lastCartPayload = cartPayload;
                shipment.setPackageSnapshotJson(writeJsonOrFallback(cartPayload, shipment.getPackageSnapshotJson()));
                shipment.setShipmentStatus(OrderShipmentStatus.CART_PENDING);
                shipmentRepository.save(shipment);
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
                JsonNode current;
                try {
                    current = fetchExternalOrder(shipment.getExternalOrderId());
                } catch (RestClientResponseException exception) {
                    int statusCode = exception.getStatusCode().value();
                    if (statusCode == 403 || statusCode == 404) {
                        JsonNode trackingResponse = melhorEnvioClient.post("/api/v2/me/shipment/tracking",
                                Map.of("orders", List.of(shipment.getExternalOrderId())));
                        current = extractTrackingRow(trackingResponse, shipment.getExternalOrderId());
                    } else {
                        throw exception;
                    }
                }
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
                JsonNode generateResponse = melhorEnvioClient.post("/api/v2/me/shipment/generate",
                        Map.of("orders", List.of(shipment.getExternalOrderId())));
                inspectGenerateResponse(shipment, generateResponse);
                shipmentRepository.save(shipment);
            }
            syncExternalShipment(order, shipment, true, false);
        } catch (RuntimeException exception) {
            if (exception instanceof RestClientResponseException responseEx) {
                int statusCode = responseEx.getStatusCode().value();
                String rawBody = responseEx.getResponseBodyAsString();
                log.warn("Melhor Envio shipment operation failed for order {} (HTTP {} - {}): {}",
                        order.getGuid(), statusCode, exception.getClass().getSimpleName(), truncate(rawBody, 600));
                if (isDefinitelyNotSavedToCart(statusCode, rawBody)
                        && shipment.getShipmentStatus() == OrderShipmentStatus.CART_PENDING
                        && shipment.getExternalOrderId() == null) {
                    shipment.setShipmentStatus(OrderShipmentStatus.SELECTED);
                }
                shipment.setLastError(formatMelhorEnvioError(statusCode, rawBody));
            } else {
                log.warn("Melhor Envio shipment operation failed for order {} ({}): {}",
                        order.getGuid(), exception.getClass().getSimpleName(), exception.getMessage());
                shipment.setLastError("Não foi possível concluir esta etapa com o Melhor Envio. Consulte a etiqueta no painel antes de repetir a operação.");
            }
            if (lastCartPayload != null) {
                log.warn("Melhor Envio /me/cart sanitized payload summary for order {}: {}",
                        order.getGuid(), summarizeCartPayloadForLog(lastCartPayload));
            }
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
        for (OrderShipment shipment : shipments) {
            boolean needsRefresh = shipment.getShipmentStatus() != OrderShipmentStatus.DELIVERED
                    && shipment.getShipmentStatus() != OrderShipmentStatus.CANCELLED
                    && (shipment.getShipmentStatus() != OrderShipmentStatus.LABEL_READY
                    || shipment.getTrackingCode() == null || shipment.getTrackingCode().isBlank());
            syncExternalShipment(order, shipment, needsRefresh, true);
        }
        return response(order, shipments);
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    @Transactional
    public void syncInTransitShipments() {
        if (!melhorEnvioClient.isConfigured()) {
            return;
        }
        List<OrderShipment> activeShipments = shipmentRepository.findAllByShipmentStatusInAndStatus(
                List.of(OrderShipmentStatus.GENERATED, OrderShipmentStatus.LABEL_READY, OrderShipmentStatus.POSTED),
                StatusCd.ACTIVE
        );
        for (OrderShipment shipment : activeShipments) {
            if (shipment.getExternalOrderId() == null || shipment.getOrderId() == null) {
                continue;
            }
            try {
                Order order = orderRepository.findById(shipment.getOrderId()).orElse(null);
                if (order == null || !StatusCd.ACTIVE.equals(order.getStatus())) {
                    continue;
                }
                OrderStatusCd status = orderStatusService.findLatest(order).getStatusCd();
                if (OrderStatusCd.READY_TO_SHIP.equals(status) || OrderStatusCd.IN_TRANSIT.equals(status)) {
                    syncExternalShipment(order, shipment, true, false);
                }
            } catch (RuntimeException exception) {
                log.warn("Scheduled shipment sync failed for shipment {} ({}): {}",
                        shipment.getGuid(), exception.getClass().getSimpleName(), exception.getMessage());
            }
        }
    }

    @Transactional
    public OrderFileContent downloadLabel(String orderGuid, String shipmentGuid) {
        Order order = loadOrder(orderGuid);
        requireMaker(order);
        OrderShipment shipment = shipmentRepository.findByGuidAndOrderIdAndStatus(
                        shipmentGuid, order.getId(), StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        boolean needsRefresh = shipment.getShipmentStatus() != OrderShipmentStatus.LABEL_READY
                && shipment.getShipmentStatus() != OrderShipmentStatus.GENERATED;
        syncExternalShipment(order, shipment, needsRefresh, true);
        if (shipment.getExternalOrderId() == null
                || (shipment.getShipmentStatus() != OrderShipmentStatus.LABEL_READY
                && shipment.getShipmentStatus() != OrderShipmentStatus.GENERATED)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        JsonNode pdfResponse = melhorEnvioClient.get("/api/v2/me/imprimir/pdf/" + shipment.getExternalOrderId());
        String labelUrl = extractFirstUrl(pdfResponse);
        if (labelUrl == null || labelUrl.isBlank()) {
            log.warn("Melhor Envio /imprimir/pdf/{} returned no PDF URL: {}",
                    shipment.getExternalOrderId(), pdfResponse == null ? "<null>" : truncate(pdfResponse.toString(), 300));
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        byte[] bytes = melhorEnvioClient.download(labelUrl);
        if (!isPdfBytes(bytes)) {
            log.warn("Melhor Envio label download for order {} did not return valid PDF bytes (length={})",
                    order.getGuid(), bytes == null ? 0 : bytes.length);
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

    private JsonNode fetchExternalOrder(String externalOrderId) {
        try {
            return melhorEnvioClient.get("/api/v2/me/orders/" + externalOrderId);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return melhorEnvioClient.get("/api/v2/me/cart/" + externalOrderId);
            }
            throw exception;
        }
    }

    private void syncExternalShipment(Order order, OrderShipment shipment, boolean forceSync, boolean allowRegenerate) {
        if (shipment.getExternalOrderId() == null
                || shipment.getShipmentStatus() == OrderShipmentStatus.DELIVERED
                || shipment.getShipmentStatus() == OrderShipmentStatus.CANCELLED) {
            return;
        }
        boolean shouldPoll = forceSync
                || shipment.getUpdateDt() == null
                || shipment.getUpdateDt().isBefore(Instant.now().minus(30, ChronoUnit.SECONDS));
        if (!shouldPoll) return;
        try {
            JsonNode status = null;
            boolean orderLookupForbidden = false;
            try {
                status = fetchExternalOrder(shipment.getExternalOrderId());
            } catch (RestClientResponseException exception) {
                int statusCode = exception.getStatusCode().value();
                if (statusCode == 403 || statusCode == 404) {
                    orderLookupForbidden = (statusCode == 403);
                    log.warn("Could not read Melhor Envio order {} via /orders or /cart (HTTP {}): falling back to /shipment/tracking",
                            shipment.getExternalOrderId(), statusCode);
                } else {
                    throw exception;
                }
            }

            if (status != null) {
                clearSyncPermissionError(shipment);
                String externalStatus = findText(status, "status", "data.status");
                if (isExternalLabelGenerated(status, externalStatus)) {
                    markLabelReadyIfNeeded(shipment);
                }
                String tracking = findText(status,
                        "tracking", "tracking_code", "trackingCode", "melhorenvio_tracking",
                        "data.tracking", "data.tracking_code", "data.melhorenvio_tracking",
                        "tracking.code", "data.tracking.code");
                String trackingUrl = findText(status, "tracking_url", "trackingUrl", "data.tracking_url", "data.url");
                applyTrackingAndDeliveryStatus(order, shipment, status, tracking, externalStatus, trackingUrl);
            }

            JsonNode latestNode = status;
            boolean shouldQueryTracking = status == null
                    || shipment.getShipmentStatus() == OrderShipmentStatus.GENERATING
                    || (shipment.getShipmentStatus() == OrderShipmentStatus.LABEL_READY
                    && (shipment.getTrackingCode() == null || shipment.getTrackingCode().isBlank()));
            if (shouldQueryTracking) {
                JsonNode trackingRow = queryTrackingForShipment(order, shipment, status == null || orderLookupForbidden);
                if (trackingRow != null) {
                    latestNode = trackingRow;
                }
            }

            handleLifecycleOrRegenerate(shipment, latestNode, allowRegenerate);
            shipmentRepository.save(shipment);
        } catch (RuntimeException exception) {
            log.warn("Could not sync Melhor Envio order {} ({}): {}",
                    shipment.getExternalOrderId(), exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private void handleLifecycleOrRegenerate(OrderShipment shipment, JsonNode latestNode, boolean allowRegenerate) {
        if (latestNode == null) {
            return;
        }
        if (findText(latestNode, "canceled_at", "data.canceled_at") != null) {
            shipment.setShipmentStatus(OrderShipmentStatus.CANCELLED);
            shipment.setLastError("Etiqueta cancelada no Melhor Envio.");
        } else if (findText(latestNode, "expired_at", "data.expired_at") != null) {
            shipment.setShipmentStatus(OrderShipmentStatus.FAILED);
            shipment.setLastError("Etiqueta expirada no Melhor Envio.");
        } else if (allowRegenerate
                && shipment.getShipmentStatus() == OrderShipmentStatus.GENERATING
                && isPaidButNotGenerated(latestNode)) {
            if (hasPrintablePdf(shipment.getExternalOrderId())) {
                markLabelReadyIfNeeded(shipment);
                shipment.setLastError(null);
                return;
            }
            try {
                JsonNode regenResp = melhorEnvioClient.post("/api/v2/me/shipment/generate",
                        Map.of("orders", List.of(shipment.getExternalOrderId())));
                log.info("Re-triggered Melhor Envio /shipment/generate for released order {}",
                        shipment.getExternalOrderId());
                inspectGenerateResponse(shipment, regenResp);
            } catch (RuntimeException exception) {
                log.warn("Could not re-trigger Melhor Envio /shipment/generate for order {} ({}): {}",
                        shipment.getExternalOrderId(), exception.getClass().getSimpleName(), exception.getMessage());
            }
        }
    }

    private boolean hasPrintablePdf(String externalOrderId) {
        try {
            JsonNode pdfResponse = melhorEnvioClient.get("/api/v2/me/imprimir/pdf/" + externalOrderId);
            String url = extractFirstUrl(pdfResponse);
            return url != null && !url.isBlank();
        } catch (RuntimeException exception) {
            log.debug("Could not check /imprimir/pdf/{} ({}): {}",
                    externalOrderId, exception.getClass().getSimpleName(), exception.getMessage());
            return false;
        }
    }

    private boolean isPaidButNotGenerated(JsonNode node) {
        return node != null && isPurchased(node) && findText(node, "generated_at", "data.generated_at") == null;
    }

    private void inspectGenerateResponse(OrderShipment shipment, JsonNode generateResponse) {
        JsonNode row = extractTrackingRow(generateResponse, shipment.getExternalOrderId());
        JsonNode target = row != null ? row : generateResponse;
        if (target != null && target.has("status")
                && target.path("status").isBoolean()
                && !target.path("status").asBoolean(true)) {
            String msg = findText(target, "message", "error");
            if (isAlreadyGeneratedMessage(msg)) {
                markLabelReadyIfNeeded(shipment);
                shipment.setLastError(null);
                return;
            }
            log.warn("Melhor Envio /shipment/generate reported status=false for order {}: {}",
                    shipment.getExternalOrderId(), msg == null ? "<none>" : truncate(msg, 240));
            if (msg != null && !msg.isBlank()) {
                shipment.setLastError("Melhor Envio ainda não gerou a etiqueta: " + truncate(msg, 240));
            }
            return;
        }
        shipment.setLastError(null);
    }

    private boolean isAlreadyGeneratedMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(msg, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return normalized.contains("ja esta gerad") || normalized.contains("already generated");
    }

    private String extractFirstUrl(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray() && !node.isEmpty()) {
            JsonNode first = node.get(0);
            if (first != null && !first.isMissingNode() && !first.isNull()) {
                if (!first.isObject() && !first.isArray() && !first.asText("").isBlank()) {
                    return first.asText();
                }
                String nested = findText(first, "url", "link");
                if (nested != null) {
                    return nested;
                }
            }
        }
        return findText(node, "url", "data.0", "data.url", "orders.0.url", "0.url", "link", "data.link");
    }

    private boolean isPdfBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private JsonNode queryTrackingForShipment(Order order, OrderShipment shipment, boolean orderLookupUnavailable) {
        try {
            JsonNode trackingResponse = melhorEnvioClient.post("/api/v2/me/shipment/tracking",
                    Map.of("orders", List.of(shipment.getExternalOrderId())));
            clearSyncPermissionError(shipment);
            JsonNode row = extractTrackingRow(trackingResponse, shipment.getExternalOrderId());
            if (row != null && !row.isMissingNode() && !row.isNull()) {
                String rowStatus = findText(row, "status", "tracking_status");
                if (isExternalLabelGenerated(row, rowStatus)) {
                    markLabelReadyIfNeeded(shipment);
                }
                String rowTracking = findText(row,
                        "tracking", "tracking_code", "code", "trackingCode",
                        "melhorenvio_tracking", "tracking.code");
                String rowTrackingUrl = findText(row, "url", "tracking_url", "trackingUrl");
                applyTrackingAndDeliveryStatus(order, shipment, row, rowTracking, rowStatus, rowTrackingUrl);
            }
            return row;
        } catch (RestClientResponseException exception) {
            int statusCode = exception.getStatusCode().value();
            log.warn("Could not query Melhor Envio /shipment/tracking for order {} (HTTP {}): {}",
                    shipment.getExternalOrderId(), statusCode, exception.getMessage());
            if (statusCode == 403 && orderLookupUnavailable) {
                shipment.setLastError("Token do Melhor Envio sem permissão (HTTP 403) para consultar a etiqueta. Habilite os escopos orders-read e shipping-tracking.");
            }
        } catch (RuntimeException exception) {
            log.warn("Could not query Melhor Envio /shipment/tracking for order {} ({}): {}",
                    shipment.getExternalOrderId(), exception.getClass().getSimpleName(), exception.getMessage());
        }
        return null;
    }

    private void clearSyncPermissionError(OrderShipment shipment) {
        if (shipment.getLastError() != null && shipment.getLastError().contains("HTTP 403")) {
            shipment.setLastError(null);
        }
    }

    private boolean isExternalLabelGenerated(JsonNode node, String externalStatus) {
        if (findText(node, "generated_at", "data.generated_at") != null) {
            return true;
        }
        return externalStatus != null
                && List.of("generated", "ready-to-print", "posted", "delivered")
                        .contains(externalStatus.toLowerCase(Locale.ROOT));
    }

    private boolean isExternalDelivered(JsonNode node, String externalStatus) {
        if (findText(node, "delivered_at", "data.delivered_at") != null) {
            return true;
        }
        return externalStatus != null
                && List.of("delivered", "entregue").contains(externalStatus.trim().toLowerCase(Locale.ROOT));
    }

    private void markLabelReadyIfNeeded(OrderShipment shipment) {
        if (shipment.getShipmentStatus() != OrderShipmentStatus.LABEL_READY
                && shipment.getShipmentStatus() != OrderShipmentStatus.DELIVERED) {
            shipment.setShipmentStatus(OrderShipmentStatus.LABEL_READY);
            if (shipment.getLabelExpiresAt() == null) {
                shipment.setLabelExpiresAt(Instant.now().plus(20, ChronoUnit.DAYS));
            }
        }
    }

    private void applyTrackingAndDeliveryStatus(Order order, OrderShipment shipment, JsonNode node,
                                                String tracking, String externalStatus, String trackingUrl) {
        boolean hasTracking = tracking != null && !tracking.isBlank();
        boolean delivered = isExternalDelivered(node, externalStatus);
        if (!hasTracking && !delivered) {
            if (externalStatus != null && !externalStatus.isBlank()
                    && order.getTrackingCode() != null && !order.getTrackingCode().isBlank()) {
                order.setTrackingStatus(externalStatus);
                orderRepository.save(order);
            }
            return;
        }
        if (hasTracking) {
            shipment.setTrackingCode(tracking);
            order.setTrackingCode(tracking);
            order.setCarrierName(shipment.getCarrierName());
            order.setTrackingUrl(trackingUrl == null || trackingUrl.isBlank()
                    ? "https://www.melhorrastreio.com.br/" : trackingUrl);
        }
        order.setTrackingStatus(externalStatus != null && !externalStatus.isBlank()
                ? externalStatus
                : (delivered ? "delivered" : order.getTrackingStatus()));
        if (delivered) {
            shipment.setShipmentStatus(OrderShipmentStatus.DELIVERED);
        }
        orderRepository.save(order);

        OrderStatusCd currentStatus = orderStatusService.findLatest(order).getStatusCd();
        if (OrderStatusCd.READY_TO_SHIP.equals(currentStatus)) {
            orderStatusService.move(order, OrderStatusCd.IN_TRANSIT);
            if (delivered) {
                orderStatusService.move(order, OrderStatusCd.COMPLETED);
            }
        } else if (delivered && OrderStatusCd.IN_TRANSIT.equals(currentStatus)) {
            orderStatusService.move(order, OrderStatusCd.COMPLETED);
        }
    }

    private JsonNode extractTrackingRow(JsonNode trackingResponse, String externalOrderId) {
        if (trackingResponse == null || trackingResponse.isMissingNode() || trackingResponse.isNull()) {
            return null;
        }
        if (trackingResponse.isObject()) {
            JsonNode keyed = trackingResponse.path(externalOrderId);
            if (keyed != null && !keyed.isMissingNode() && !keyed.isNull()) {
                return keyed;
            }
            JsonNode dataNode = trackingResponse.path("data");
            if (dataNode.isObject()) {
                JsonNode nestedKeyed = dataNode.path(externalOrderId);
                if (nestedKeyed != null && !nestedKeyed.isMissingNode() && !nestedKeyed.isNull()) {
                    return nestedKeyed;
                }
            } else if (dataNode.isArray() && !dataNode.isEmpty()) {
                return dataNode.get(0);
            }
        } else if (trackingResponse.isArray() && !trackingResponse.isEmpty()) {
            return trackingResponse.get(0);
        }
        return null;
    }

    private boolean isPurchased(JsonNode current) {
        if (findText(current, "paid_at", "data.paid_at") != null) {
            return true;
        }
        String status = findText(current, "status", "data.status", "purchase.status", "data.purchase.status");
        return status != null && List.of("paid", "released", "purchased", "approved").contains(status.toLowerCase());
    }

    private Map<String, Object> readCartSnapshot(Order order, JsonNode snapshot) {
        JsonNode cart = snapshot.path("cart");
        if (!cart.isObject()) throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        Map<String, Object> payload = objectMapper.convertValue(cart, new TypeReference<>() {});
        normalizeCartPayload(payload, snapshot.path("quote"), order);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private void normalizeCartPayload(Map<String, Object> payload, JsonNode quoteNode, Order order) {
        BigDecimal volumeInsurance = null;
        JsonNode quotePackages = quoteNode == null ? null : quoteNode.path("packages");
        Object rawVolumes = payload.get("volumes");
        if (rawVolumes instanceof List<?> volumeList) {
            for (int i = 0; i < volumeList.size(); i++) {
                Object entry = volumeList.get(i);
                if (entry instanceof Map<?, ?> rawMap) {
                    Map<String, Object> volumeMap = (Map<String, Object>) rawMap;
                    Object removed = volumeMap.remove("insurance");
                    if (volumeInsurance == null && removed != null) {
                        try {
                            volumeInsurance = new BigDecimal(String.valueOf(removed));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    JsonNode pkgNode = quotePackages != null && quotePackages.isArray() && i < quotePackages.size()
                            ? quotePackages.get(i) : null;
                    JsonNode pkgDims = pkgNode == null ? null : pkgNode.path("dimensions");

                    volumeMap.put("width", resolveNormalizedDimCm(
                            volumeMap.get("width"), pkgDims == null ? null : pkgDims.path("width"), ShippingQuoteService.MIN_WIDTH_CM));
                    volumeMap.put("height", resolveNormalizedDimCm(
                            volumeMap.get("height"), pkgDims == null ? null : pkgDims.path("height"), ShippingQuoteService.MIN_HEIGHT_CM));
                    volumeMap.put("length", resolveNormalizedDimCm(
                            volumeMap.get("length"), pkgDims == null ? null : pkgDims.path("length"), ShippingQuoteService.MIN_LENGTH_CM));
                    volumeMap.put("weight", resolveNormalizedWeightKg(
                            volumeMap.get("weight"), pkgNode == null ? null : pkgNode.path("weight")));
                }
            }
        }

        Map<String, Object> options;
        if (payload.get("options") instanceof Map<?, ?> rawOptions) {
            options = (Map<String, Object>) rawOptions;
        } else {
            options = new LinkedHashMap<>();
            payload.put("options", options);
        }
        if (!options.containsKey("non_commercial") && !options.containsKey("invoice")) {
            options.put("non_commercial", true);
        }
        options.putIfAbsent("reverse", false);
        if (options.get("insurance_value") == null) {
            BigDecimal resolvedInsurance = volumeInsurance != null
                    ? volumeInsurance
                    : computeProductsInsuredValue(payload.get("products"));
            options.put("insurance_value", resolvedInsurance.setScale(2, RoundingMode.HALF_UP));
        }

        if (payload.get("from") instanceof Map<?, ?> fromMap) {
            normalizeCartContact((Map<String, Object>) fromMap, order == null ? null : order.getFromAddressId());
        }
        if (payload.get("to") instanceof Map<?, ?> toMap) {
            normalizeCartContact((Map<String, Object>) toMap, order == null ? null : order.getToAddressId());
        }
    }

    private int resolveNormalizedDimCm(Object volumeVal, JsonNode quoteDimNode, int minCm) {
        BigDecimal volDec = ShippingQuoteService.parsePositiveDecimal(volumeVal);
        BigDecimal quoteDec = ShippingQuoteService.parsePositiveDecimal(quoteDimNode);
        BigDecimal effective = volDec;
        if (quoteDec != null && (effective == null || quoteDec.compareTo(effective) > 0)) {
            effective = quoteDec;
        }
        return ShippingQuoteService.clampDimensionCm(effective, minCm);
    }

    private BigDecimal resolveNormalizedWeightKg(Object volumeVal, JsonNode quoteWeightNode) {
        BigDecimal volDec = ShippingQuoteService.parsePositiveDecimal(volumeVal);
        BigDecimal quoteDec = ShippingQuoteService.parsePositiveDecimal(quoteWeightNode);
        BigDecimal effective = volDec != null ? volDec : quoteDec;
        if (volDec != null && quoteDec != null && quoteDec.compareTo(volDec) > 0) {
            effective = quoteDec;
        }
        return ShippingQuoteService.clampWeightKg(effective);
    }

    private BigDecimal computeProductsInsuredValue(Object rawProducts) {
        BigDecimal total = BigDecimal.ZERO;
        if (rawProducts instanceof List<?> productsList) {
            for (Object item : productsList) {
                if (item instanceof Map<?, ?> prodMap) {
                    Object qtyObj = prodMap.get("quantity");
                    Object valObj = prodMap.get("unitary_value");
                    try {
                        BigDecimal qty = qtyObj == null ? BigDecimal.ONE : new BigDecimal(String.valueOf(qtyObj));
                        BigDecimal val = valObj == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(valObj));
                        total = total.add(val.multiply(qty));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return total;
    }

    private void normalizeCartContact(Map<String, Object> contact, Long addressId) {
        Object phoneObj = contact.get("phone");
        if (phoneObj != null) {
            String phoneDigits = ShippingQuoteService.digitsOnly(String.valueOf(phoneObj));
            if (phoneDigits.startsWith("55") && (phoneDigits.length() == 12 || phoneDigits.length() == 13)) {
                contact.put("phone", phoneDigits.substring(2));
            } else if (!phoneDigits.isBlank()) {
                contact.put("phone", phoneDigits);
            }
        }
        Object companyDocObj = contact.get("company_document");
        if (companyDocObj != null) {
            String compDigits = ShippingQuoteService.digitsOnly(String.valueOf(companyDocObj));
            if (compDigits.length() == 11) {
                Object existingDoc = contact.get("document");
                if (existingDoc == null || String.valueOf(existingDoc).isBlank()) {
                    contact.put("document", compDigits);
                }
                contact.remove("company_document");
                contact.remove("state_register");
            } else if (!compDigits.isBlank()) {
                contact.put("company_document", compDigits);
            }
        }

        for (String key : List.of("name", "email", "address", "number", "city")) {
            if (contact.get(key) != null) {
                contact.put(key, String.valueOf(contact.get(key)).trim());
            }
        }

        String complement = contact.get("complement") == null ? "" : String.valueOf(contact.get("complement")).trim();
        if (complement.isBlank()) {
            contact.remove("complement");
        } else {
            contact.put("complement", complement);
        }

        String district = contact.get("district") == null ? "" : String.valueOf(contact.get("district")).trim();
        if (district.isBlank() && addressId != null) {
            district = addressRepository.findById(addressId)
                    .map(Address::getNeighborhood)
                    .map(String::trim)
                    .orElse("");
        }
        contact.put("district", district.isBlank() ? "Centro" : district);

        if (contact.get("state_abbr") != null) {
            contact.put("state_abbr", ShippingQuoteService.normalizeBrazilState(String.valueOf(contact.get("state_abbr"))));
        }
    }

    private String validateDistinctDocuments(Map<String, Object> cartPayload) {
        Map<?, ?> fromMap = cartPayload.get("from") instanceof Map<?, ?> map ? map : null;
        Map<?, ?> toMap = cartPayload.get("to") instanceof Map<?, ?> map ? map : null;
        String fromDoc = effectiveDocument(fromMap);
        String toDoc = effectiveDocument(toMap);
        if (!fromDoc.isBlank() && fromDoc.equals(toDoc)) {
            return "Remetente e destinatário não podem ter o mesmo CPF/CNPJ no Melhor Envio. Utilize contas/documentos diferentes.";
        }
        return null;
    }

    private String effectiveDocument(Map<?, ?> contact) {
        if (contact == null) {
            return "";
        }
        Object compObj = contact.get("company_document");
        String compDigits = compObj == null ? "" : ShippingQuoteService.digitsOnly(String.valueOf(compObj));
        if (!compDigits.isBlank()) {
            return compDigits;
        }
        Object docObj = contact.get("document");
        return docObj == null ? "" : ShippingQuoteService.digitsOnly(String.valueOf(docObj));
    }

    private void resolveAgencyIfRequired(Map<String, Object> cartPayload, JsonNode quoteNode, Order order) {
        if (cartPayload.get("agency") != null) {
            return;
        }
        int quoteCompanyId = quoteNode != null ? quoteNode.path("company").path("id").asInt(-1) : -1;
        int serviceId = order.getShippingServiceId() == null ? -1 : order.getShippingServiceId();
        String carrierName = order.getCarrierName() != null
                ? order.getCarrierName()
                : quoteNode == null ? "" : quoteNode.path("company").path("name").asText("");
        if (ShippingQuoteService.isCorreiosCarrier(quoteCompanyId, serviceId, carrierName)) {
            // Melhor Envio /api/v2/me/cart returns HTTP 500 when agency is omitted for Correios.
            cartPayload.put("agency", 1);
            return;
        }
        int companyId = quoteCompanyId;
        if (companyId <= 0 && ((serviceId == 3 || serviceId == 4)
                || (carrierName != null && carrierName.toLowerCase(Locale.ROOT).contains("jadlog")))) {
            companyId = 2;
        }
        if (companyId <= 0) {
            return;
        }
        Map<?, ?> fromMap = cartPayload.get("from") instanceof Map<?, ?> map ? map : null;
        String state = fromMap == null ? "" : sanitizeQueryParamValue(fromMap.get("state_abbr"));
        String city = fromMap == null ? "" : sanitizeQueryParamValue(fromMap.get("city"));

        List<String> candidates = new ArrayList<>();
        if (!state.isBlank() && !city.isBlank()) {
            candidates.add("/api/v2/me/shipment/agencies?company=" + companyId + "&country=BR&state=" + state + "&city=" + city);
        }
        if (!state.isBlank()) {
            candidates.add("/api/v2/me/shipment/agencies?company=" + companyId + "&country=BR&state=" + state);
        }
        candidates.add("/api/v2/me/shipment/agencies?company=" + companyId);

        for (String path : candidates) {
            try {
                JsonNode response = melhorEnvioClient.get(path);
                Integer agencyId = extractAvailableAgencyId(response);
                if (agencyId != null) {
                    cartPayload.put("agency", agencyId);
                    return;
                }
            } catch (RuntimeException exception) {
                log.warn("Could not resolve Melhor Envio agency via {} ({}): {}",
                        path, exception.getClass().getSimpleName(), exception.getMessage());
            }
        }
    }

    private Integer extractAvailableAgencyId(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return null;
        }
        JsonNode rows = response.isArray() ? response : response.path("data");
        if (!rows.isArray()) {
            return null;
        }
        for (JsonNode row : rows) {
            int id = row.path("id").asInt(-1);
            if (id <= 0) continue;
            String status = row.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            if ("unavailable".equals(status) || "inactive".equals(status)) {
                continue;
            }
            return id;
        }
        return null;
    }

    private String sanitizeQueryParamValue(Object raw) {
        if (raw == null) return "";
        return String.valueOf(raw)
                .replaceAll("[\\{\\}\\[\\]?&#=]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isDefinitelyNotSavedToCart(int statusCode, String rawBody) {
        if (statusCode >= 400 && statusCode < 500) {
            return true;
        }
        return statusCode == 500
                && rawBody != null
                && rawBody.toLowerCase(Locale.ROOT).contains("erro ao salvar o pedido no carrinho");
    }

    private Map<String, Object> summarizeCartPayloadForLog(Map<String, Object> payload) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("service", payload.get("service"));
        if (payload.containsKey("agency")) {
            summary.put("agency", payload.get("agency"));
        }
        Map<?, ?> fromMap = payload.get("from") instanceof Map<?, ?> map ? map : null;
        Map<?, ?> toMap = payload.get("to") instanceof Map<?, ?> map ? map : null;
        summary.put("from", summarizeContactForLog(fromMap));
        summary.put("to", summarizeContactForLog(toMap));
        String fromDoc = effectiveDocument(fromMap);
        String toDoc = effectiveDocument(toMap);
        summary.put("sameEffectiveDoc", !fromDoc.isBlank() && fromDoc.equals(toDoc));
        summary.put("productsCount", payload.get("products") instanceof List<?> list ? list.size() : 0);
        summary.put("volumes", payload.get("volumes"));
        summary.put("options", payload.get("options"));
        return summary;
    }

    private Map<String, Object> summarizeContactForLog(Map<?, ?> contact) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (contact == null) {
            return info;
        }
        info.put("city", contact.get("city"));
        info.put("state", contact.get("state_abbr"));
        info.put("postalCode", contact.get("postal_code"));
        Object districtObj = contact.get("district");
        info.put("districtNotBlank", districtObj != null && !String.valueOf(districtObj).isBlank());
        info.put("hasComplement", contact.containsKey("complement"));
        Object docObj = contact.get("document");
        Object compDocObj = contact.get("company_document");
        Object phoneObj = contact.get("phone");
        Object emailObj = contact.get("email");
        info.put("docLen", docObj == null ? 0 : ShippingQuoteService.digitsOnly(String.valueOf(docObj)).length());
        info.put("compDocLen", compDocObj == null ? 0 : ShippingQuoteService.digitsOnly(String.valueOf(compDocObj)).length());
        info.put("phoneLen", phoneObj == null ? 0 : ShippingQuoteService.digitsOnly(String.valueOf(phoneObj)).length());
        info.put("hasEmail", emailObj != null && !String.valueOf(emailObj).isBlank());
        return info;
    }

    private String writeJsonOrFallback(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String formatMelhorEnvioError(int statusCode, String rawBody) {
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rawBody);
                StringBuilder details = new StringBuilder();
                String message = findText(root, "message", "error");
                if (message != null) {
                    details.append(message.trim());
                }
                JsonNode errors = root.path("errors");
                if (errors.isObject()) {
                    for (var entry : errors.properties()) {
                        String field = entry.getKey();
                        JsonNode val = entry.getValue();
                        String errMsg = val.isArray() && !val.isEmpty() ? val.get(0).asText("") : val.asText("");
                        if (!errMsg.isBlank()) {
                            if (!details.isEmpty()) details.append("; ");
                            details.append(field).append(": ").append(errMsg.trim());
                        }
                    }
                }
                if (!details.isEmpty()) {
                    return "Melhor Envio recusou os dados do envio (HTTP " + statusCode + "): " + truncate(details.toString(), 320);
                }
            } catch (Exception ignored) {
                return "Melhor Envio recusou os dados do envio (HTTP " + statusCode + "): " + truncate(rawBody, 220);
            }
        }
        return "Melhor Envio recusou a operação (HTTP " + statusCode + "). Confira os dados de endereço, documento e telefone.";
    }

    private String truncate(String value, int maxLen) {
        if (value == null || value.isBlank()) return "<empty>";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > maxLen ? compact.substring(0, maxLen) + "..." : compact;
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
