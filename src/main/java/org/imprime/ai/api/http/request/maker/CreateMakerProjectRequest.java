package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Dados para criação de um projeto multi-peça com acompanhamento BOM")
public record CreateMakerProjectRequest(
        @Schema(description = "Nome do projeto", example = "Capacete Cosplay Modular", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do projeto é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @Schema(description = "Escopo e detalhes do projeto", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        @Schema(description = "Nome do cliente", example = "Rafael Souza")
        @Size(max = 120, message = "O nome do cliente deve ter no máximo 120 caracteres")
        String clientName,

        @Schema(description = "GUID do pedido vinculado (opcional)")
        String orderGuid,

        @Schema(description = "Data limite de entrega (YYYY-MM-DD)", example = "2026-06-05", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A data limite é obrigatória")
        LocalDate dueDate,

        @Schema(description = "Total de mesas/placas de impressão", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O total de mesas é obrigatório")
        @Min(value = 1, message = "O projeto deve ter ao menos 1 mesa")
        Integer totalPlates,

        @Schema(description = "Total de peças do projeto", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O total de peças é obrigatório")
        @Min(value = 1, message = "O projeto deve ter ao menos 1 peça")
        Integer totalParts,

        @Schema(description = "Peso estimado total em gramas", example = "850.0")
        @Positive(message = "O peso estimado deve ser positivo")
        BigDecimal estimatedWeightGrams,

        @Schema(description = "Horas estimadas totais de impressão", example = "24.0")
        @Positive(message = "As horas estimadas devem ser positivas")
        BigDecimal estimatedHours,

        @Schema(description = "Valor cobrado do cliente em BRL", example = "420.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor orçado é obrigatório")
        @Positive(message = "O valor orçado deve ser positivo")
        BigDecimal quotedPrice,

        @Schema(description = "Custo estimado de produção em BRL", example = "115.00")
        @PositiveOrZero(message = "O custo estimado não pode ser negativo")
        BigDecimal estimatedCost,

        @Schema(description = "Material predominante", example = "PETG", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O material predominante é obrigatório")
        MaterialType primaryMaterial,

        @Schema(description = "Cor principal em hexadecimal #RRGGBB", example = "#a855f7")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor hexadecimal inválida")
        String colorHex,

        @Schema(description = "Observações internas de produção")
        @Size(max = 1000, message = "As observações devem ter no máximo 1000 caracteres")
        String notes
) {
}
