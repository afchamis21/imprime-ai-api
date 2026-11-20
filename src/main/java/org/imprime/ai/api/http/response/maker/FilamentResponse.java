package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Schema(description = "Item de inventário de filamento ou resina do Maker")
public record FilamentResponse(
        @Schema(description = "GUID único do rolo/frasco")
        String guid,
        @Schema(description = "Nome identificador", example = "PLA Matte Branco Neve")
        String name,
        @Schema(description = "Marca/fabricante", example = "Bambu Lab")
        String brand,
        @Schema(description = "Material", example = "PLA")
        MaterialType material,
        @Schema(description = "Nome da cor", example = "Branco")
        String colorName,
        @Schema(description = "Cor em hexadecimal", example = "#f1f5f9")
        String colorHex,
        @Schema(description = "Saldo disponível em gramas", example = "760.0")
        BigDecimal stockGrams,
        @Schema(description = "Peso nominal inicial em gramas", example = "1000.0")
        BigDecimal initialWeightGrams,
        @Schema(description = "Custo por kg em BRL", example = "135.00")
        BigDecimal costPerKg,
        @Schema(description = "Diâmetro em mm", example = "1.75")
        BigDecimal diameterMm,
        @Schema(description = "Limite de alerta em gramas", example = "250.0")
        BigDecimal lowStockThresholdGrams,
        @Schema(description = "Indica se o estoque atual está abaixo ou igual ao limite de alerta", example = "false")
        boolean lowStock,
        @Schema(description = "Umidade relativa medida (%)", example = "18")
        Integer humidityPercent,
        @Schema(description = "Localização física no estúdio", example = "AMS Slot A1")
        String spoolLocation,
        @Schema(description = "Observações operacionais")
        String notes
) {
}
