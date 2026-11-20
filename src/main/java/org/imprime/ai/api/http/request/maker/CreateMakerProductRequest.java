package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.http.request.common.DimensionsRequest;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados para cadastro de produto na loja do Maker")
public record CreateMakerProductRequest(
        @Schema(description = "Nome comercial do produto", example = "Suporte Modular para Headset RGB", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do produto é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @Schema(description = "Preço de venda em BRL", example = "65.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O preço é obrigatório")
        @Positive(message = "O preço deve ser maior que zero")
        BigDecimal price,

        @Schema(description = "Preço anterior promocional em BRL (opcional)", example = "79.90")
        @Positive(message = "O preço anterior deve ser maior que zero")
        BigDecimal oldPrice,

        @Schema(description = "Categoria do produto", example = "utilities", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A categoria é obrigatória")
        @Size(max = 80, message = "A categoria deve ter no máximo 80 caracteres")
        String category,

        @Schema(description = "Lista de URLs das imagens do produto", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Informe ao menos uma imagem do produto")
        @Size(max = 8, message = "Máximo de 8 imagens por produto")
        List<@NotBlank String> imageUrls,

        @Schema(description = "URL da imagem principal de capa")
        String coverImageUrl,

        @Schema(description = "Descrição detalhada do produto")
        @Size(max = 4000, message = "A descrição deve ter no máximo 4000 caracteres")
        String description,

        @Schema(description = "Material de fabricação", example = "PETG", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O material é obrigatório")
        MaterialType material,

        @Schema(description = "Dimensões físicas da peça em milímetros")
        @Valid
        DimensionsRequest dimensions,

        @Schema(description = "Peso aproximado em gramas", example = "140")
        @Positive(message = "O peso deve ser positivo")
        Integer weightGrams,

        @Schema(description = "Destaques / características do produto")
        @Size(max = 15, message = "Máximo de 15 destaques")
        List<String> features,

        @Schema(description = "Indica se o produto está disponível para encomenda", example = "true")
        Boolean inStock,

        @Schema(description = "Prazo estimado de produção em dias", example = "3")
        @Min(value = 1, message = "O prazo mínimo de produção é 1 dia")
        Integer productionDays,

        @Schema(description = "Cores disponíveis para personalização", example = "[\"Preto\", \"Branco\"]")
        List<String> customizableColors
) {
}
