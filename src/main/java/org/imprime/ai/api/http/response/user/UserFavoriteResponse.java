package org.imprime.ai.api.http.response.user;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.FavoriteTargetType;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Item salvo na lista de favoritos do usuário")
public record UserFavoriteResponse(
        @Schema(description = "GUID do registro de favorito", example = "22222222-3333-4444-5555-666666666666")
        String favoriteGuid,
        @Schema(description = "Tipo da entidade favoritada", example = "MAKER")
        FavoriteTargetType targetType,
        @Schema(description = "GUID da entidade favoritada", example = "550e8400-e29b-41d4-a716-446655440000")
        String targetGuid,
        @Schema(description = "Slug da entidade favoritada para navegação no frontend", example = "studio-impressao-3d")
        String targetSlug,
        @Schema(description = "Nome do Maker, produto ou modelo 3D", example = "Studio Impressão 3D")
        String name,
        @Schema(description = "Resumo/descrição curta")
        String description,
        @Schema(description = "Cidade (quando aplicável, ex.: Maker)", example = "São Paulo")
        String city,
        @Schema(description = "UF (quando aplicável, ex.: Maker)", example = "SP")
        String state,
        @Schema(description = "Nota média do item favoritado", example = "4.90")
        BigDecimal rating,
        @Schema(description = "Total de avaliações", example = "128")
        int reviewsCount,
        @Schema(description = "URL da imagem principal ou avatar")
        String coverImageUrl,
        @Schema(description = "Data em que foi favoritado em UTC (ISO-8601)")
        Instant favoritedAt
) {
}
