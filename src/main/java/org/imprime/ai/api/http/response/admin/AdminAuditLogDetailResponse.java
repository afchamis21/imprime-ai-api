package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.AuditAction;

import java.time.Instant;

@Schema(description = "Detalhes completos de um registro de log de auditoria administrativa")
public record AdminAuditLogDetailResponse(
        @Schema(description = "GUID único do log de auditoria")
        String guid,
        @Schema(description = "GUID do administrador autor da ação")
        String adminUserGuid,
        @Schema(description = "E-mail do administrador autor da ação")
        String adminEmail,
        @Schema(description = "Nome completo do administrador")
        String adminName,
        @Schema(description = "Ação executada")
        AuditAction action,
        @Schema(description = "Tipo da entidade alvo")
        String targetType,
        @Schema(description = "GUID da entidade alvo")
        String targetGuid,
        @Schema(description = "Motivo ou observação da ação administrativa")
        String reason,
        @Schema(description = "Snapshot JSON do estado anterior à ação")
        String beforeState,
        @Schema(description = "Snapshot JSON do estado posterior à ação")
        String afterState,
        @Schema(description = "Endereço IP de origem da requisição")
        String ipAddress,
        @Schema(description = "User-Agent do cliente")
        String userAgent,
        @Schema(description = "Data/hora UTC da ação")
        Instant createdAt
) {
}
