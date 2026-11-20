package org.imprime.ai.api.http.request.print;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Schema(description = "Parâmetros técnicos do fatiamento para estimativa de custo de impressão sob demanda")
public record PrintEstimateRequest(
        @Schema(description = "Peso estimado de material em gramas", example = "85.5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O peso em gramas é obrigatório")
        @Positive(message = "O peso deve ser maior que zero")
        BigDecimal weightGrams,

        @Schema(description = "Tempo estimado de impressão em minutos", example = "150", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O tempo estimado em minutos é obrigatório")
        @Min(value = 1, message = "O tempo estimado deve ser de pelo menos 1 minuto")
        Integer printTimeMinutes,

        @Schema(description = "Material selecionado para impressão", example = "PLA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O material é obrigatório")
        MaterialType material,

        @Schema(description = "Quantidade de cópias/unidades", example = "1")
        @Min(value = 1, message = "A quantidade mínima é 1")
        @Max(value = 1000, message = "A quantidade máxima por cotação é 1000")
        Integer quantity
) {
}
