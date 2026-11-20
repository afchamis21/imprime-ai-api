package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Ponto diário da tendência de produção")
public record DailyPrintTrendDto(
        @Schema(description = "Data de referência (YYYY-MM-DD)", example = "2026-05-18")
        LocalDate date,
        @Schema(description = "Horas impressas no dia", example = "10.0")
        BigDecimal hours,
        @Schema(description = "Quantidade de impressões", example = "5")
        int printsCount,
        @Schema(description = "Consumo de material em gramas", example = "320.0")
        BigDecimal grams
) {
}
