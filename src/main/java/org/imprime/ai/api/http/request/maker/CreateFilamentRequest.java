package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Schema(description = "Dados para cadastro de um rolo de filamento ou frasco de resina no inventário do Maker")
public record CreateFilamentRequest(
        @Schema(description = "Nome identificador do rolo/resina", example = "PETG Azul Safira", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do filamento é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String name,

        @Schema(description = "Marca/fabricante", example = "Bambu Lab", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A marca é obrigatória")
        @Size(max = 80, message = "A marca deve ter no máximo 80 caracteres")
        String brand,

        @Schema(description = "Tipo de material", example = "PETG", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O material é obrigatório")
        MaterialType material,

        @Schema(description = "Nome da cor", example = "Azul", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome da cor é obrigatório")
        @Size(max = 60, message = "O nome da cor deve ter no máximo 60 caracteres")
        String colorName,

        @Schema(description = "Cor hexadecimal no formato #RRGGBB", example = "#3b82f6", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O código hexadecimal da cor é obrigatório")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor hexadecimal inválida (use #RRGGBB)")
        String colorHex,

        @Schema(description = "Saldo disponível em gramas", example = "1000.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O saldo em gramas é obrigatório")
        @PositiveOrZero(message = "O saldo não pode ser negativo")
        BigDecimal stockGrams,

        @Schema(description = "Peso nominal inicial em gramas (padrão 1000)", example = "1000.0")
        @Positive(message = "O peso inicial deve ser positivo")
        BigDecimal initialWeightGrams,

        @Schema(description = "Custo por quilograma em BRL", example = "130.00")
        @PositiveOrZero(message = "O custo por kg não pode ser negativo")
        BigDecimal costPerKg,

        @Schema(description = "Diâmetro do filamento em mm (0 para resina)", example = "1.75")
        @PositiveOrZero(message = "O diâmetro não pode ser negativo")
        BigDecimal diameterMm,

        @Schema(description = "Limite em gramas para alerta de estoque baixo", example = "250.0")
        @PositiveOrZero(message = "O limite de alerta não pode ser negativo")
        BigDecimal lowStockThresholdGrams,

        @Schema(description = "Umidade relativa medida (%)", example = "20")
        @Min(value = 0, message = "A umidade mínima é 0%")
        @Max(value = 100, message = "A umidade máxima é 100%")
        Integer humidityPercent,

        @Schema(description = "Localização física no estúdio", example = "Drybox #1")
        @Size(max = 100, message = "A localização deve ter no máximo 100 caracteres")
        String spoolLocation,

        @Schema(description = "Observações operacionais")
        @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres")
        String notes
) {
}
