package org.imprime.ai.api.http.response.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.common.OrderItemResponse;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.OrderStatusCd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhes completos de um pedido com endereço de entrega, itens e histórico de status")
public record OrderDetailResponse(
        @Schema(description = "GUID único do pedido", example = "c3f4a190-6b7c-4f21-98a4-112233445566")
        String orderGuid,
        @Schema(description = "Código legível de exibição do pedido", example = "PED-C3F4A1")
        String orderCode,
        @Schema(description = "Status atual do pedido", example = "CONFIRMED")
        OrderStatusCd status,
        @Schema(description = "Subtotal dos itens em BRL", example = "120.00")
        BigDecimal subtotal,
        @Schema(description = "Valor do frete em BRL", example = "18.90")
        BigDecimal shippingCost,
        @Schema(description = "Valor total do pedido em BRL", example = "138.90")
        BigDecimal totalAmount,
        @Schema(description = "Código de rastreio")
        String trackingCode,
        @Schema(description = "Nome da transportadora do envio")
        String carrierName,
        @Schema(description = "Status atual do envio")
        String trackingStatus,
        @Schema(description = "URL segura de acompanhamento do envio")
        String trackingUrl,
        @Schema(description = "Observações do pedido")
        String notes,
        @Schema(description = "Endereço de entrega do pedido")
        AddressDTO shippingAddress,
        @Schema(description = "Resumo do Maker responsável (nulo enquanto em cotação aberta)")
        MakerBriefResponse maker,
        @Schema(description = "Dados do comprador")
        UserDTO buyer,
        @Schema(description = "Itens pertencentes ao pedido")
        List<OrderItemResponse> items,
        @Schema(description = "Linha do tempo de transições de status do pedido")
        List<OrderStatusHistoryResponse> statusHistory,
        @Schema(description = "Data de criação em UTC (ISO-8601)")
        Instant createdAt,
        @Schema(description = "Data da última atualização em UTC (ISO-8601)")
        Instant updatedAt
) {
    public OrderDetailResponse(
            String orderGuid,
            String orderCode,
            OrderStatusCd status,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal totalAmount,
            String trackingCode,
            String notes,
            AddressDTO shippingAddress,
            MakerBriefResponse maker,
            UserDTO buyer,
            List<OrderItemResponse> items,
            List<OrderStatusHistoryResponse> statusHistory,
            Instant createdAt,
            Instant updatedAt) {
        this(orderGuid, orderCode, status, subtotal, shippingCost, totalAmount,
                trackingCode, null, null, null, notes, shippingAddress, maker, buyer,
                items, statusHistory, createdAt, updatedAt);
    }
}
