package org.imprime.ai.api.http.response.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.NegotiationStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detalhes de uma conversa de negociação e últimas mensagens")
public record ChatDetailResponse(
        @Schema(description = "ID UUID do chat", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        UUID chatId,
        @Schema(description = "GUID do pedido associado")
        String orderGuid,
        @Schema(description = "Código legível do pedido", example = "PED-C3F4A1")
        String orderCode,
        @Schema(description = "Título do projeto em negociação", example = "Peça Impressa em 3D")
        String projectTitle,
        @Schema(description = "Valor acordado ou última oferta ativa em BRL", example = "150.00")
        BigDecimal agreedOrQuotedPrice,
        @Schema(description = "Status atual do pedido vinculado", example = "QUOTING")
        OrderStatusCd orderStatus,
        @Schema(description = "Status da negociação no chat", example = "QUOTE_RECEIVED")
        NegotiationStatus negotiationStatus,
        @Schema(description = "Quantidade de mensagens não lidas", example = "0")
        int unreadCount,
        @Schema(description = "Dados do comprador")
        UserDTO client,
        @Schema(description = "Dados resumidos do Maker")
        MakerBriefResponse maker,
        @Schema(description = "Mensagens mais recentes (histórico completo paginado via `GET /chats/{chatId}/messages`)")
        List<MessageDTO> recentMessages,
        @Schema(description = "Data de criação do chat em UTC (ISO-8601)")
        Instant createdAt,
        @Schema(description = "Data da última mensagem em UTC (ISO-8601)")
        Instant lastMessageAt
) {
}
