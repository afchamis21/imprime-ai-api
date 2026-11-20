package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.PixKeyType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Resumo financeiro consolidado do Maker (extrato detalhado em `/maker/finances/transactions`)")
public record MakerFinanceSummaryResponse(
        @Schema(description = "Saldo disponível para saque imediato em BRL", example = "680.00")
        BigDecimal availableBalance,
        @Schema(description = "Saldo pendente (pedidos em andamento/liberação) em BRL", example = "1250.00")
        BigDecimal pendingBalance,
        @Schema(description = "Receita do mês corrente em BRL", example = "3860.00")
        BigDecimal monthlyRevenue,
        @Schema(description = "Despesas do mês corrente em BRL", example = "1036.60")
        BigDecimal monthlyExpenses,
        @Schema(description = "Lucro líquido do mês em BRL", example = "2823.40")
        BigDecimal netProfit,
        @Schema(description = "Margem de lucro percentual", example = "73.1")
        BigDecimal profitMarginPercent,
        @Schema(description = "Chave PIX mascarada para exibição segura", example = "fin***@studio3d.com.br")
        String pixKeyMasked,
        @Schema(description = "Tipo da chave PIX configurada", example = "EMAIL")
        PixKeyType pixKeyType,
        @Schema(description = "Tarifa configurada de energia elétrica (BRL/kWh)", example = "0.92")
        BigDecimal kwhRate,
        @Schema(description = "Taxa horária de depreciação por impressora (BRL/hora)", example = "1.80")
        BigDecimal hourlyDepreciationRate,
        @Schema(description = "Distribuição de custos operacionais por categoria")
        List<CostBreakdownDto> costBreakdown,
        @Schema(description = "Série mensal de receitas e despesas")
        List<MonthlyFinanceSeriesDto> monthlySeries
) {
}
