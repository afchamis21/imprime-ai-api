package org.imprime.ai.api.http.response.product;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Item resumido de produto no catálogo público")
public record ProductCatalogItemResponse(
        @Schema(description = "GUID único do produto", example = "a1b2c3d4-0000-4000-8000-000000000101")
        String guid,
        @Schema(description = "Slug amigável do produto", example = "vaso-geometrico-modular")
        String slug,
        @Schema(description = "Nome do produto", example = "Vaso Geométrico Modular")
        String name,
        @Schema(description = "Categoria do produto", example = "decoration")
        String category,
        @Schema(description = "Preço de venda em BRL", example = "45.00")
        BigDecimal price,
        @Schema(description = "Preço anterior promocional em BRL (opcional)", example = "59.90")
        BigDecimal oldPrice,
        @Schema(description = "Nota média (0.00 a 5.00)", example = "4.90")
        BigDecimal rating,
        @Schema(description = "Total de avaliações", example = "87")
        int reviewsCount,
        @Schema(description = "Material de fabricação", example = "PLA")
        MaterialType material,
        @Schema(description = "Cor principal em hexadecimal", example = "#8B5CF6")
        String colorHex,
        @Schema(description = "URL da imagem de capa")
        String coverImageUrl,
        @Schema(description = "Indica se está disponível para encomenda", example = "true")
        boolean inStock,
        @Schema(description = "Indica se foi favoritado pelo usuário autenticado", example = "false")
        boolean favorited,
        @Schema(description = "Resumo do Maker vendedor")
        MakerBriefResponse maker,
        @Schema(description = "Data de criação em UTC (ISO-8601)", example = "2026-05-14T12:00:00Z")
        Instant createdAt
) {
}
