package org.imprime.ai.api.http.response.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.OrderStatusCd;

import java.time.Instant;

@Schema(description = "Registro histórico de mudança de status de um pedido")
public record OrderStatusHistoryResponse(
        @Schema(description = "Código do status atingido", example = "CONFIRMED")
        OrderStatusCd status,
        @Schema(description = "Data e hora da transição em UTC (ISO-8601)", example = "2026-11-19T14:25:00Z")
        Instant statusDate,
        @Schema(description = "GUID do usuário responsável pela transição")
        String changedByUserGuid
) {
}
