package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.imprime.ai.api.model.enums.QuoteDepreciationMode;
import org.imprime.ai.api.model.enums.QuotePricingStrategy;

import java.math.BigDecimal;

@Schema(description = "Dados fixos padrão da calculadora de orçamento do Maker")
public record UpdateMakerQuoteSettingsRequest(
        @Schema(description = "Tarifa de energia elétrica da região em BRL/kWh", example = "0.9200", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A tarifa de energia (kWh) é obrigatória")
        @Positive(message = "A tarifa de energia (kWh) deve ser positiva")
        BigDecimal kwhRate,

        @Schema(description = "Consumo elétrico padrão em Watts para modo manual ou fallback", example = "300", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O consumo padrão (Watts) é obrigatório")
        @Positive(message = "O consumo padrão (Watts) deve ser positivo")
        Integer defaultPowerWatts,

        @Schema(description = "Modo padrão de cálculo de depreciação", example = "FIXED_HOURLY", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O modo de depreciação é obrigatório")
        QuoteDepreciationMode depreciationMode,

        @Schema(description = "Depreciação e manutenção fixa padrão em BRL/hora", example = "2.50", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A depreciação horária é obrigatória")
        @PositiveOrZero(message = "A depreciação horária não pode ser negativa")
        BigDecimal hourlyDepreciationRate,

        @Schema(description = "Valor médio de aquisição da impressora em BRL para cálculo por vida útil", example = "3500.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor padrão da máquina é obrigatório")
        @PositiveOrZero(message = "O valor padrão da máquina não pode ser negativo")
        BigDecimal defaultMachinePrice,

        @Schema(description = "Vida útil estimada padrão da máquina em horas", example = "3000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A vida útil padrão em horas é obrigatória")
        @Positive(message = "A vida útil padrão em horas deve ser positiva")
        Integer defaultMachineLifespanHours,

        @Schema(description = "Custo adicional horário de manutenção preventiva em BRL/hora", example = "0.50", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O custo horário de manutenção é obrigatório")
        @PositiveOrZero(message = "O custo horário de manutenção não pode ser negativo")
        BigDecimal defaultMaintenanceHourlyCost,

        @Schema(description = "Taxa padrão de falha e desperdício em percentual (%)", example = "10.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A taxa de falha é obrigatória")
        @DecimalMin(value = "0.00", message = "A taxa de falha não pode ser negativa")
        @DecimalMax(value = "100.00", message = "A taxa de falha deve ser de no máximo 100%")
        BigDecimal failureRatePercent,

        @Schema(description = "Valor da hora técnica de mão de obra em BRL/hora", example = "35.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor da hora de mão de obra é obrigatório")
        @PositiveOrZero(message = "O valor da hora de mão de obra não pode ser negativo")
        BigDecimal laborHourlyRate,

        @Schema(description = "Custos fixos de insumos e embalagem por pedido em BRL", example = "5.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O custo fixo de embalagem/insumos é obrigatório")
        @PositiveOrZero(message = "O custo fixo de embalagem/insumos não pode ser negativo")
        BigDecimal fixedPackagingCost,

        @Schema(description = "Margem de lucro padrão em percentual (%)", example = "50.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A margem de lucro padrão é obrigatória")
        @PositiveOrZero(message = "A margem de lucro padrão não pode ser negativa")
        BigDecimal defaultProfitMarginPercent,

        @Schema(description = "Taxas adicionais padrão (maquininha, marketplace, impostos) em percentual (%)", example = "0.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "As taxas adicionais são obrigatórias")
        @DecimalMin(value = "0.00", message = "As taxas adicionais não podem ser negativas")
        @DecimalMax(value = "94.99", message = "As taxas adicionais devem ser inferiores a 95%")
        BigDecimal additionalFeePercent,

        @Schema(description = "Estratégia padrão de precificação", example = "MARKUP", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A estratégia de precificação é obrigatória")
        QuotePricingStrategy pricingStrategy
) {
}
