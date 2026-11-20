package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.DimensionsDto;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Dados de um produto na gestão da loja do Maker")
public record MakerProductResponse(
        @Schema(description = "GUID único do produto")
        String guid,
        @Schema(description = "Slug único gerado pelo servidor")
        String slug,
        @Schema(description = "Nome comercial do produto")
        String name,
        @Schema(description = "Preço de venda em BRL")
        BigDecimal price,
        @Schema(description = "Preço anterior promocional em BRL (opcional)")
        BigDecimal oldPrice,
        @Schema(description = "Categoria do produto")
        String category,
        @Schema(description = "URL da imagem principal de capa")
        String coverImageUrl,
        @Schema(description = "URLs das imagens do produto")
        List<String> imageUrls,
        @Schema(description = "Descrição detalhada")
        String description,
        @Schema(description = "Material de fabricação")
        MaterialType material,
        @Schema(description = "Dimensões físicas em milímetros")
        DimensionsDto dimensions,
        @Schema(description = "Peso aproximado em gramas")
        Integer weightGrams,
        @Schema(description = "Destaques / características")
        List<String> features,
        @Schema(description = "Disponibilidade em estoque")
        boolean inStock,
        @Schema(description = "Prazo estimado de produção em dias")
        Integer productionDays,
        @Schema(description = "Cores disponíveis para personalização")
        List<String> customizableColors,
        @Schema(description = "Data de criação em UTC (ISO-8601)")
        Instant createdAt,
        @Schema(description = "Data da última atualização em UTC (ISO-8601)")
        Instant updatedAt
) {
}
