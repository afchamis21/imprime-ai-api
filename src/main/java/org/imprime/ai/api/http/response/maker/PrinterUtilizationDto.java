package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.PrinterStatus;

import java.math.BigDecimal;

@Schema(description = "Taxa de utilização por impressora no período")
public record PrinterUtilizationDto(
        @Schema(description = "GUID da impressora")
        String printerGuid,
        @Schema(description = "Nome da impressora", example = "X1C Principal")
        String name,
        @Schema(description = "Modelo da máquina", example = "Bambu Lab X1 Carbon")
        String model,
        @Schema(description = "Status operacional atual", example = "PRINTING")
        PrinterStatus status,
        @Schema(description = "Horas trabalhadas no período", example = "140.0")
        BigDecimal hours,
        @Schema(description = "Total de impressões executadas", example = "45")
        int printsCount,
        @Schema(description = "Percentual de utilização no período", example = "67.0")
        BigDecimal utilizationPercent
) {
}
