package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.quote.QuoteCalculationResult;
import org.imprime.ai.api.quote.QuoteValidationWarning;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Resultado consolidado em tempo real do motor modular de orçamento 3D")
public record MakerQuoteCalculationResponse(
        @Schema(description = "Indica se os parâmetros matemáticos da simulação são válidos", example = "true")
        boolean isValid,
        @Schema(description = "Parâmetros efetivos resolvidos durante o pipeline")
        EffectiveParametersResponse effective,
        @Schema(description = "Lista ordenada de parcelas calculadas pelos módulos ativos")
        List<QuotePartBreakdownResponse> parts,
        @Schema(description = "Totais e valores unitários consolidados da simulação")
        BreakdownSummaryResponse breakdown,
        @Schema(description = "Detalhamento de consumo e estoque por filamento")
        List<FilamentBreakdownResponse> filaments,
        @Schema(description = "Alertas informativos, avisos de estoque/pedido mínimo e erros de validação")
        List<QuoteWarningResponse> warnings
) {
    @Schema(description = "Parâmetros efetivamente aplicados no cálculo")
    public record EffectiveParametersResponse(
            int quantity,
            String inputScope,
            Integer unitsPerPlate,
            int estimatedPlates,
            BigDecimal unitPrintHours,
            BigDecimal totalPrintHours,
            BigDecimal unitWeightGrams,
            BigDecimal totalWeightGrams,
            BigDecimal powerWatts,
            String powerSource,
            BigDecimal unitEnergyKwh,
            BigDecimal totalEnergyKwh,
            BigDecimal hourlyDepreciationRate,
            String depreciationSource,
            boolean requiresLabor,
            int effectiveLaborTimeMinutes,
            BigDecimal failureRatePercent,
            BigDecimal profitMarginPercent,
            BigDecimal additionalFeePercent,
            BigDecimal discountPercent,
            String activeProfitStrategy,
            String extraCostsScope
    ) {
    }

    @Schema(description = "Resumo financeiro consolidado em visão lote total e unitária")
    public record BreakdownSummaryResponse(
            BreakdownAmountsResponse total,
            BreakdownAmountsResponse unit,
            BigDecimal realProfitMarginOnPricePercent,
            BigDecimal realMarkupOnCostPercent
    ) {
    }

    @Schema(description = "Valores monetários em BRL")
    public record BreakdownAmountsResponse(
            BigDecimal materialCost,
            BigDecimal energyCost,
            BigDecimal depreciationCost,
            BigDecimal directPrintCost,
            BigDecimal failureReserveCost,
            BigDecimal laborCost,
            BigDecimal packagingCost,
            BigDecimal extraCosts,
            BigDecimal productionCost,
            BigDecimal grossPriceBeforeDiscount,
            BigDecimal discountAmount,
            BigDecimal feesCost,
            BigDecimal suggestedFinalPrice,
            BigDecimal minimumBreakEvenPrice,
            BigDecimal estimatedNetProfit
    ) {
    }

    @Schema(description = "Detalhamento individual de um filamento da simulação")
    public record FilamentBreakdownResponse(
            String entryId,
            String label,
            String mode,
            BigDecimal costPerKg,
            BigDecimal costPerGram,
            BigDecimal unitWeightGrams,
            BigDecimal totalWeightGrams,
            BigDecimal unitCost,
            BigDecimal totalCost,
            BigDecimal availableStockGrams,
            boolean exceedsStock
    ) {
    }

    @Schema(description = "Alerta ou erro estruturado do motor de cálculo")
    public record QuoteWarningResponse(
            String code,
            String severity,
            String message
    ) {
    }

    public static MakerQuoteCalculationResponse from(QuoteCalculationResult result) {
        QuoteCalculationResult.EffectiveParameters eff = result.effective();
        EffectiveParametersResponse effectiveResponse = new EffectiveParametersResponse(
                eff.quantity(),
                eff.inputScope(),
                eff.unitsPerPlate(),
                eff.estimatedPlates(),
                eff.unitPrintHours(),
                eff.totalPrintHours(),
                eff.unitWeightGrams(),
                eff.totalWeightGrams(),
                eff.powerWatts(),
                eff.powerSource(),
                eff.unitEnergyKwh(),
                eff.totalEnergyKwh(),
                eff.hourlyDepreciationRate(),
                eff.depreciationSource(),
                eff.requiresLabor(),
                eff.effectiveLaborTimeMinutes(),
                eff.failureRatePercent(),
                eff.profitMarginPercent(),
                eff.additionalFeePercent(),
                eff.discountPercent(),
                eff.activeProfitStrategy(),
                eff.extraCostsScope()
        );

        List<QuotePartBreakdownResponse> partsResponse = result.parts().stream()
                .map(QuotePartBreakdownResponse::from)
                .toList();

        BreakdownSummaryResponse breakdownResponse = new BreakdownSummaryResponse(
                toAmounts(result.breakdown().total()),
                toAmounts(result.breakdown().unit()),
                result.breakdown().realProfitMarginOnPricePercent(),
                result.breakdown().realMarkupOnCostPercent()
        );

        List<FilamentBreakdownResponse> filamentsResponse = result.filaments().stream()
                .map(f -> new FilamentBreakdownResponse(
                        f.entryId(),
                        f.label(),
                        f.mode(),
                        f.costPerKg(),
                        f.costPerGram(),
                        f.unitWeightGrams(),
                        f.totalWeightGrams(),
                        f.unitCost(),
                        f.totalCost(),
                        f.availableStockGrams(),
                        f.exceedsStock()
                ))
                .toList();

        List<QuoteWarningResponse> warningsResponse = result.warnings().stream()
                .map(MakerQuoteCalculationResponse::toWarning)
                .toList();

        return new MakerQuoteCalculationResponse(
                result.valid(),
                effectiveResponse,
                partsResponse,
                breakdownResponse,
                filamentsResponse,
                warningsResponse
        );
    }

    private static BreakdownAmountsResponse toAmounts(QuoteCalculationResult.BreakdownAmounts amounts) {
        return new BreakdownAmountsResponse(
                amounts.materialCost(),
                amounts.energyCost(),
                amounts.depreciationCost(),
                amounts.directPrintCost(),
                amounts.failureReserveCost(),
                amounts.laborCost(),
                amounts.packagingCost(),
                amounts.extraCosts(),
                amounts.productionCost(),
                amounts.grossPriceBeforeDiscount(),
                amounts.discountAmount(),
                amounts.feesCost(),
                amounts.suggestedFinalPrice(),
                amounts.minimumBreakEvenPrice(),
                amounts.estimatedNetProfit()
        );
    }

    private static QuoteWarningResponse toWarning(QuoteValidationWarning warning) {
        return new QuoteWarningResponse(warning.code(), warning.severity(), warning.message());
    }
}
