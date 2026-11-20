package org.imprime.ai.api.http.response.support;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;

import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhes completos de um chamado de suporte e histórico de mensagens")
public record SupportTicketDetailResponse(
        @Schema(description = "GUID único do chamado")
        String ticketGuid,
        @Schema(description = "Código de protocolo legível", example = "SUP-0042")
        String protocolCode,
        @Schema(description = "Assunto do chamado")
        String subject,
        @Schema(description = "Categoria do chamado", example = "ORDER")
        SupportTicketCategory category,
        @Schema(description = "Status atual", example = "OPEN")
        SupportTicketStatus status,
        @Schema(description = "GUID do pedido vinculado (opcional)")
        String orderGuid,
        @Schema(description = "Mensagens do chamado")
        List<SupportTicketMessageResponse> messages,
        @Schema(description = "Data de abertura em UTC (ISO-8601)")
        Instant createdAt,
        @Schema(description = "Data da última atualização em UTC (ISO-8601)")
        Instant lastUpdatedAt
) {
}
