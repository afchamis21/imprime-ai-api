package org.imprime.ai.api.http.response.user;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.ReviewTargetType;

import java.time.Instant;

@Schema(description = "Avaliação enviada pelo usuário autenticado")
public record UserReviewResponse(
        @Schema(description = "GUID da avaliação", example = "8a2f7f10-3c90-4a12-b89e-111122223333")
        String reviewGuid,
        @Schema(description = "Tipo do alvo avaliado", example = "MAKER")
        ReviewTargetType targetType,
        @Schema(description = "GUID do alvo avaliado")
        String targetGuid,
        @Schema(description = "Nome do Maker, produto ou modelo avaliado", example = "Studio Impressão 3D")
        String targetName,
        @Schema(description = "GUID do pedido relacionado (quando aplicável)")
        String orderGuid,
        @Schema(description = "Nota atribuída (1 a 5)", example = "5")
        int rating,
        @Schema(description = "Comentário publicado")
        String comment,
        @Schema(description = "Data de publicação em UTC (ISO-8601)")
        Instant createdAt
) {
}
