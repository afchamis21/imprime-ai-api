package org.imprime.ai.api.http.response.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Resumo público de identificação do Maker")
public record MakerBriefResponse(
        @Schema(description = "GUID único do Maker (empresa)", example = "550e8400-e29b-41d4-a716-446655440000")
        String guid,
        @Schema(description = "Slug amigável do Maker", example = "studio-impressao-3d")
        String slug,
        @Schema(description = "Nome do estúdio/Maker", example = "Studio Impressão 3D")
        String name,
        @Schema(description = "URL pública do avatar", example = "https://cdn.imprime.ai/makers/studio3d/avatar.png")
        String avatarUrl,
        @Schema(description = "Média de avaliações (0.00 a 5.00)", example = "4.90")
        BigDecimal rating,
        @Schema(description = "Quantidade total de avaliações recebidas", example = "128")
        int reviewsCount,
        @Schema(description = "Indica se o Maker é verificado", example = "true")
        boolean verified
) {
}
