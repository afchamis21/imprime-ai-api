package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.AdminOrderType;
import org.imprime.ai.api.model.enums.OrderStatusCd;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Alerta de pedido com SLA operacional atrasado")
public record AdminSlaAlertResponse(
        @Schema(description = "GUID do pedido")
        String orderGuid,
        @Schema(description = "Código legível do pedido (PED-...)")
        String orderCode,
        @Schema(description = "Status atual do pedido")
        OrderStatusCd status,
        @Schema(description = "Classificação do pedido (DIGITAL, PHYSICAL, HYBRID)")
        AdminOrderType orderType,
        @Schema(description = "Severidade do atraso (CRITICAL ou WARNING)")
        String severity,
        @Schema(description = "Horas de atraso além do prazo esperado")
        long delayHours,
        @Schema(description = "Prazo limite esperado em UTC")
        Instant expectedDeadlineAt,
        @Schema(description = "Motivo do alerta de SLA")
        String reason,
        @Schema(description = "Nome do comprador")
        String buyerName,
        @Schema(description = "Nome do Maker responsável")
        String makerName,
        @Schema(description = "Valor total do pedido")
        BigDecimal orderTotal,
        @Schema(description = "Data de criação do pedido")
        Instant createdAt
) {
}
