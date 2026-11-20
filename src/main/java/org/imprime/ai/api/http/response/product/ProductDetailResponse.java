package org.imprime.ai.api.http.response.product;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.DimensionsDto;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhes completos de um produto do marketplace")
public record ProductDetailResponse(
        @Schema(description = "GUID único do produto", example = "a1b2c3d4-0000-4000-8000-000000000101")
        String guid,
        @Schema(description = "Slug amigável do produto", example = "suporte-universal-controle")
        String slug,
        @Schema(description = "Nome comercial do produto", example = "Suporte Universal para Controle de Videogame")
        String name,
        @Schema(description = "Preço de venda em BRL", example = "45.90")
        BigDecimal price,
        @Schema(description = "Preço anterior promocional em BRL (opcional)", example = "59.90")
        BigDecimal oldPrice,
        @Schema(description = "Categoria do produto", example = "gamer-accessories")
        String category,
        @Schema(description = "Nota média (0.00 a 5.00)", example = "4.80")
        BigDecimal rating,
        @Schema(description = "Total de avaliações", example = "32")
        int reviewsCount,
        @Schema(description = "Quantidade total de vendas", example = "154")
        int salesCount,
        @Schema(description = "Descrição completa do produto")
        String description,
        @Schema(description = "Destaques/características do produto")
        List<String> features,
        @Schema(description = "Material de fabricação", example = "PETG")
        MaterialType material,
        @Schema(description = "Dimensões físicas em milímetros")
        DimensionsDto dimensions,
        @Schema(description = "Peso aproximado em gramas", example = "120")
        Integer weightGrams,
        @Schema(description = "Prazo estimado de produção em dias", example = "2")
        Integer productionDays,
        @Schema(description = "Cores disponíveis para personalização")
        List<String> customizableColors,
        @Schema(description = "Disponibilidade em estoque/produção", example = "true")
        boolean inStock,
        @Schema(description = "Indica se está nos favoritos do usuário autenticado", example = "false")
        boolean favorited,
        @Schema(description = "URLs da galeria de imagens")
        List<String> imageUrls,
        @Schema(description = "Dados resumidos do Maker vendedor")
        MakerBriefResponse maker,
        @Schema(description = "Data de publicação em UTC (ISO-8601)")
        Instant createdAt
) {
}
