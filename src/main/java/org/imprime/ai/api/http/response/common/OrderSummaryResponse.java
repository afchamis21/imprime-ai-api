package org.imprime.ai.api.http.response.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.OrderStatusCd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Visão resumida de um pedido para listagens de comprador e Maker")
public record OrderSummaryResponse(
        @Schema(description = "GUID único do pedido", example = "c3f4a190-6b7c-4f21-98a4-112233445566")
        String orderGuid,
        @Schema(description = "Código legível de exibição do pedido", example = "PED-C3F4A1")
        String orderCode,
        @Schema(description = "Status atual do pedido", example = "IN_PRODUCTION")
        OrderStatusCd status,
        @Schema(description = "Subtotal dos itens em BRL", example = "120.00")
        BigDecimal subtotal,
        @Schema(description = "Valor do frete em BRL", example = "18.90")
        BigDecimal shippingCost,
        @Schema(description = "Valor total do pedido em BRL", example = "138.90")
        BigDecimal totalAmount,
        @Schema(description = "Código de rastreio da transportadora/Correios")
        String trackingCode,
        @Schema(description = "Nome da transportadora do envio")
        String carrierName,
        @Schema(description = "Status atual do envio")
        String trackingStatus,
        @Schema(description = "URL segura de acompanhamento do envio")
        String trackingUrl,
        @Schema(description = "Observações do pedido")
        String notes,
        @Schema(description = "Resumo do Maker responsável pelo pedido")
        MakerBriefResponse maker,
        @Schema(description = "Dados básicos do comprador")
        UserDTO buyer,
        @Schema(description = "Itens do pedido")
        List<OrderItemResponse> items,
        @Schema(description = "Data de criação em UTC (ISO-8601)", example = "2026-11-19T14:20:00Z")
        Instant createdAt,
        @Schema(description = "Data da última atualização em UTC (ISO-8601)", example = "2026-11-19T16:00:00Z")
        Instant updatedAt
) {
    public OrderSummaryResponse(
            String orderGuid,
            String orderCode,
            OrderStatusCd status,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal totalAmount,
            String trackingCode,
            String notes,
            MakerBriefResponse maker,
            UserDTO buyer,
            List<OrderItemResponse> items,
            Instant createdAt,
            Instant updatedAt) {
        this(orderGuid, orderCode, status, subtotal, shippingCost, totalAmount,
                trackingCode, null, null, null, notes, maker, buyer, items, createdAt, updatedAt);
    }
}
