package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.QuotePartResult;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "Parcela individual calculada por um módulo do motor modular")
public record QuotePartBreakdownResponse(
        @Schema(description = "Código do módulo", example = "MATERIAL")
        String code,
        @Schema(description = "Título exibido no breakdown", example = "Material / Filamento")
        String title,
        @Schema(description = "Tipo/fase do módulo", example = "BASE_COST")
        QuoteModuleType quoteType,
        @Schema(description = "Ordem de execução aplicada", example = "10")
        int executionOrder,
        @Schema(description = "Indica se o módulo esteve ativo neste cálculo", example = "true")
        boolean enabled,
        @Schema(description = "Indica se recebeu sobrescrita pontual nesta cotação", example = "false")
        boolean overridden,
        @Schema(description = "Valor unitário em BRL", example = "7.20")
        BigDecimal unitAmount,
        @Schema(description = "Valor total do lote em BRL", example = "14.40")
        BigDecimal totalAmount,
        @Schema(description = "Participação percentual sobre o custo de produção (%)", example = "28.44")
        BigDecimal shareOfProductionCostPercent,
        @Schema(description = "Resumo legível dos parâmetros da parcela", example = "60 g/un · 120 g total")
        String detailLabel,
        @Schema(description = "Metadados complementares da parcela")
        Map<String, Object> metadata
) {
    public static QuotePartBreakdownResponse from(QuotePartResult part) {
        return new QuotePartBreakdownResponse(
                part.code(),
                part.title(),
                part.quoteType(),
                part.executionOrder(),
                part.enabled(),
                part.overridden(),
                part.unitAmount(),
                part.totalAmount(),
                part.shareOfProductionCostPercent(),
                part.detailLabel(),
                part.metadata()
        );
    }
}
