package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Ponto diário do mapa de calor de atividade do estúdio")
public record ActivityHeatmapPointDto(
        @Schema(description = "Data (YYYY-MM-DD)", example = "2026-05-18")
        LocalDate date,
        @Schema(description = "Quantidade de impressões realizadas", example = "2")
        int printsCount,
        @Schema(description = "Horas totais de impressão no dia", example = "4.7")
        BigDecimal hours,
        @Schema(description = "Nível discreto de intensidade de atividade (0 a 4)", example = "2")
        int intensityLevel
) {
}
