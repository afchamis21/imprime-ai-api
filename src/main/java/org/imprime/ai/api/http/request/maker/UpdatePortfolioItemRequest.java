package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados para atualização parcial (PATCH) de um item do portfólio do Maker")
public record UpdatePortfolioItemRequest(
        @Schema(description = "Título atualizado", example = "Dragão Articulado Dual-Color V2")
        @Size(min = 1, max = 150, message = "O título deve ter entre 1 e 150 caracteres")
        String title,

        @Schema(description = "Material atualizado", example = "PLA")
        MaterialType material,

        @Schema(description = "Nova lista de URLs das imagens")
        @Size(min = 1, max = 8, message = "Informe entre 1 e 8 imagens")
        List<String> imageUrls,

        @Schema(description = "Nova URL da imagem de capa")
        String coverImageUrl,

        @Schema(description = "Nova descrição técnica")
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        @Schema(description = "Tempo total de impressão em minutos", example = "310")
        @Positive(message = "O tempo de impressão deve ser positivo")
        Integer printTimeMinutes,

        @Schema(description = "Impressora utilizada", example = "Bambu Lab X1 Carbon")
        @Size(max = 120, message = "O nome da impressora deve ter no máximo 120 caracteres")
        String printerUsed,

        @Schema(description = "Altura de camada em milímetros", example = "0.16")
        @Positive(message = "A altura de camada deve ser positiva")
        BigDecimal layerHeightMm,

        @Schema(description = "Lista atualizada de tags")
        @Size(max = 10, message = "Máximo de 10 tags")
        List<String> tags
) {
}
