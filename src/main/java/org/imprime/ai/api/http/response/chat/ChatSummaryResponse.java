package org.imprime.ai.api.http.response.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.NegotiationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Resumo de uma sala de chat e negociação de cotação")
public record ChatSummaryResponse(
        @Schema(description = "ID UUID do chat", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        UUID chatId,
        @Schema(description = "GUID do pedido associado", example = "c3f4a190-6b7c-4f21-98a4-112233445566")
        String orderGuid,
        @Schema(description = "Código legível do pedido", example = "PED-C3F4A1")
        String orderCode,
        @Schema(description = "Título ou descrição resumida do projeto cotado", example = "Suporte de Headset")
        String projectTitle,
        @Schema(description = "Material previsto para a cotação", example = "PLA")
        MaterialType material,
        @Schema(description = "Status atual da negociação", example = "QUOTE_RECEIVED")
        NegotiationStatus status,
        @Schema(description = "Último valor ofertado em BRL (quando houver oferta)", example = "115.00")
        BigDecimal latestQuotedPrice,
        @Schema(description = "Prazo estimado em dias informado pelo Maker", example = "3")
        Integer estimatedLeadTimeDays,
        @Schema(description = "Quantidade de mensagens não lidas pelo usuário autenticado", example = "2")
        int unreadCount,
        @Schema(description = "Prévia da última mensagem enviada")
        String lastMessagePreview,
        @Schema(description = "Data e hora da última mensagem em UTC (ISO-8601)")
        Instant lastMessageAt,
        @Schema(description = "Dados do comprador participante")
        UserDTO client,
        @Schema(description = "Dados resumidos do Maker participante")
        MakerBriefResponse maker
) {
}
