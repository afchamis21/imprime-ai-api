package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.http.request.common.DimensionsRequest;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados para atualização parcial (PATCH) de um produto do Maker")
public record UpdateMakerProductRequest(
        @Schema(description = "Nome comercial atualizado")
        @Size(min = 1, max = 150, message = "O nome deve ter entre 1 e 150 caracteres")
        String name,

        @Schema(description = "Preço atualizado em BRL", example = "42.90")
        @Positive(message = "O preço deve ser maior que zero")
        BigDecimal price,

        @Schema(description = "Novo preço anterior promocional (0 remove promoção)", example = "59.90")
        @PositiveOrZero(message = "O preço anterior deve ser maior ou igual a zero")
        BigDecimal oldPrice,

        @Schema(description = "Categoria atualizada")
        @Size(max = 80, message = "A categoria deve ter no máximo 80 caracteres")
        String category,

        @Schema(description = "Nova lista de URLs das imagens")
        @Size(min = 1, max = 8, message = "Informe entre 1 e 8 imagens")
        List<String> imageUrls,

        @Schema(description = "Nova URL da imagem principal de capa")
        String coverImageUrl,

        @Schema(description = "Descrição atualizada")
        @Size(max = 4000, message = "A descrição deve ter no máximo 4000 caracteres")
        String description,

        @Schema(description = "Material atualizado")
        MaterialType material,

        @Schema(description = "Dimensões físicas atualizadas")
        @Valid
        DimensionsRequest dimensions,

        @Schema(description = "Peso aproximado em gramas")
        @Positive(message = "O peso deve ser positivo")
        Integer weightGrams,

        @Schema(description = "Lista atualizada de destaques")
        @Size(max = 15, message = "Máximo de 15 destaques")
        List<String> features,

        @Schema(description = "Disponibilidade para encomenda")
        Boolean inStock,

        @Schema(description = "Prazo de produção em dias")
        @Min(value = 1, message = "O prazo mínimo de produção é 1 dia")
        Integer productionDays,

        @Schema(description = "Cores personalizáveis atualizadas")
        List<String> customizableColors
) {
}
