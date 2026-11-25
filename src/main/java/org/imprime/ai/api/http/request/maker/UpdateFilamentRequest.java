package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Schema(description = "Dados para atualização parcial (PATCH) de um filamento ou resina")
public record UpdateFilamentRequest(
        @Schema(description = "Nome identificador atualizado")
        @Size(min = 1, max = 120, message = "O nome deve ter entre 1 e 120 caracteres")
        String name,

        @Schema(description = "Marca atualizada")
        @Size(min = 1, max = 80, message = "A marca deve ter entre 1 e 80 caracteres")
        String brand,

        @Schema(description = "Material atualizado")
        MaterialType material,

        @Schema(description = "Nome da cor atualizado")
        @Size(max = 60, message = "O nome da cor deve ter no máximo 60 caracteres")
        String colorName,

        @Schema(description = "Código hexadecimal #RRGGBB atualizado", example = "#f1f5f9")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor hexadecimal inválida (use #RRGGBB)")
        String colorHex,

        @Schema(description = "Saldo atualizado em gramas")
        @PositiveOrZero(message = "O saldo não pode ser negativo")
        BigDecimal stockGrams,

        @Schema(description = "Peso nominal inicial em gramas")
        @Positive(message = "O peso inicial deve ser positivo")
        BigDecimal initialWeightGrams,

        @Schema(description = "Custo por kg em BRL")
        @PositiveOrZero(message = "O custo por kg não pode ser negativo")
        BigDecimal costPerKg,

        @Schema(description = "Diâmetro em mm")
        @PositiveOrZero(message = "O diâmetro não pode ser negativo")
        BigDecimal diameterMm,

        @Schema(description = "Limite de alerta de estoque baixo em gramas")
        @PositiveOrZero(message = "O limite de alerta não pode ser negativo")
        BigDecimal lowStockThresholdGrams,

        @Schema(description = "Umidade relativa (%)")
        @Min(value = 0, message = "A umidade mínima é 0%")
        @Max(value = 100, message = "A umidade máxima é 100%")
        Integer humidityPercent,

        @Schema(description = "Localização física no estúdio")
        @Size(max = 100, message = "A localização deve ter no máximo 100 caracteres")
        String spoolLocation,

        @Schema(description = "Observações operacionais")
        @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres")
        String notes,

        @Schema(description = "GUID opcional do perfil no catálogo global de filamentos")
        String filamentCatalogGuid
) {
    public UpdateFilamentRequest(
            String name,
            String brand,
            MaterialType material,
            String colorName,
            String colorHex,
            BigDecimal stockGrams,
            BigDecimal initialWeightGrams,
            BigDecimal costPerKg,
            BigDecimal diameterMm,
            BigDecimal lowStockThresholdGrams,
            Integer humidityPercent,
            String spoolLocation,
            String notes
    ) {
        this(
                name, brand, material, colorName, colorHex, stockGrams, initialWeightGrams,
                costPerKg, diameterMm, lowStockThresholdGrams, humidityPercent, spoolLocation, notes, null
        );
    }
}
