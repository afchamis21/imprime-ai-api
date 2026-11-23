package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.QuoteDepreciationMode;
import org.imprime.ai.api.model.enums.QuotePricingStrategy;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Parâmetros fixos vigentes para a calculadora de orçamento do Maker")
public record MakerQuoteSettingsResponse(
        @Schema(description = "Tarifa de energia elétrica em BRL/kWh", example = "0.9200")
        BigDecimal kwhRate,
        @Schema(description = "Consumo elétrico padrão em Watts", example = "300")
        Integer defaultPowerWatts,
        @Schema(description = "Modo de cálculo de depreciação", example = "FIXED_HOURLY")
        QuoteDepreciationMode depreciationMode,
        @Schema(description = "Depreciação e manutenção fixa em BRL/hora", example = "2.50")
        BigDecimal hourlyDepreciationRate,
        @Schema(description = "Valor médio da máquina em BRL", example = "3500.00")
        BigDecimal defaultMachinePrice,
        @Schema(description = "Vida útil estimada padrão em horas", example = "3000")
        Integer defaultMachineLifespanHours,
        @Schema(description = "Custo horário de manutenção preventiva em BRL/hora", example = "0.50")
        BigDecimal defaultMaintenanceHourlyCost,
        @Schema(description = "Taxa padrão de falha e desperdício (%)", example = "10.00")
        BigDecimal failureRatePercent,
        @Schema(description = "Valor da hora de mão de obra em BRL/hora", example = "35.00")
        BigDecimal laborHourlyRate,
        @Schema(description = "Custo fixo de embalagem/insumos por pedido em BRL", example = "5.00")
        BigDecimal fixedPackagingCost,
        @Schema(description = "Margem de lucro padrão (%)", example = "50.00")
        BigDecimal defaultProfitMarginPercent,
        @Schema(description = "Taxas adicionais padrão (%)", example = "0.00")
        BigDecimal additionalFeePercent,
        @Schema(description = "Estratégia padrão de precificação", example = "MARKUP")
        QuotePricingStrategy pricingStrategy,
        @Schema(description = "Indica se o estúdio já salvou parâmetros personalizados", example = "true")
        boolean customized,
        @Schema(description = "Data/hora da última atualização dos parâmetros de orçamento")
        Instant updatedAt
) {
}
