package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.StatsRange;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Métricas consolidadas de produção, consumo e utilização da frota do Maker")
public record MakerStatsResponse(
        @Schema(description = "Janela temporal consultada", example = "LAST_30_DAYS")
        StatsRange range,
        @Schema(description = "Total de horas de impressão", example = "214.0")
        BigDecimal totalPrintHours,
        @Schema(description = "Total de impressões no período", example = "94")
        int totalPrints,
        @Schema(description = "Impressões concluídas com sucesso", example = "86")
        int successfulPrints,
        @Schema(description = "Impressões com falha", example = "5")
        int failedPrints,
        @Schema(description = "Impressões canceladas", example = "3")
        int cancelledPrints,
        @Schema(description = "Taxa de sucesso percentual", example = "91.5")
        BigDecimal successRatePercent,
        @Schema(description = "Total de filamento/resina consumido em gramas", example = "6480.0")
        BigDecimal totalFilamentGrams,
        @Schema(description = "Consumo estimado de energia em kWh", example = "62.1")
        BigDecimal totalEnergyKwh,
        @Schema(description = "Custo estimado de energia elétrica em BRL", example = "57.13")
        BigDecimal estimatedEnergyCost,
        @Schema(description = "Duração média por impressão em horas", example = "2.3")
        BigDecimal avgPrintDurationHours,
        @Schema(description = "Distribuição de consumo por material")
        List<MaterialBreakdownDto> materialBreakdown,
        @Schema(description = "Utilização detalhada por impressora")
        List<PrinterUtilizationDto> printerUtilization,
        @Schema(description = "Série diária de tendência de produção")
        List<DailyPrintTrendDto> dailyTrend,
        @Schema(description = "Mapa de calor diário de atividade")
        List<ActivityHeatmapPointDto> heatmap
) {
}
