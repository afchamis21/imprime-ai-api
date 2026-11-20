package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

@Schema(description = "Série mensal de receitas e despesas do estúdio")
public record MonthlyFinanceSeriesDto(
        @Schema(description = "Mês de referência no formato ISO YYYY-MM", example = "2026-05")
        YearMonth month,
        @Schema(description = "Receita bruta no mês em BRL", example = "3860.00")
        BigDecimal revenue,
        @Schema(description = "Despesas totais no mês em BRL", example = "1020.00")
        BigDecimal expenses
) {
}
