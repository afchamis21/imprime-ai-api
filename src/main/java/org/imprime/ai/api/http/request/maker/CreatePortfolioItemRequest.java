package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados para publicação de um projeto no portfólio do Maker (imagens devem ser enviadas via `/media/upload`)")
public record CreatePortfolioItemRequest(
        @Schema(description = "Título do trabalho realizado", example = "Dragão Articulado Dual-Color", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O título é obrigatório")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres")
        String title,

        @Schema(description = "Material principal utilizado", example = "PLA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O material é obrigatório")
        MaterialType material,

        @Schema(description = "Lista de URLs das imagens previamente enviadas via `/media/upload` (1 a 8 imagens)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Informe ao menos uma imagem")
        @Size(max = 8, message = "Máximo de 8 imagens por item de portfólio")
        List<@NotBlank String> imageUrls,

        @Schema(description = "URL da imagem de capa (se omitida, usa a primeira imagem de `imageUrls`)")
        String coverImageUrl,

        @Schema(description = "Descrição técnica e detalhes de acabamento")
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        @Schema(description = "Tempo total de impressão em minutos", example = "270")
        @Positive(message = "O tempo de impressão deve ser positivo")
        Integer printTimeMinutes,

        @Schema(description = "Modelo da impressora utilizada", example = "Bambu Lab X1 Carbon")
        @Size(max = 120, message = "O nome da impressora deve ter no máximo 120 caracteres")
        String printerUsed,

        @Schema(description = "Altura de camada em milímetros", example = "0.20")
        @Positive(message = "A altura de camada deve ser positiva")
        BigDecimal layerHeightMm,

        @Schema(description = "Tags de categorização", example = "[\"Impressão 3D\", \"Articulado\"]")
        @Size(max = 10, message = "Máximo de 10 tags")
        List<String> tags
) {
}
