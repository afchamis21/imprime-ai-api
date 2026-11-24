package org.imprime.ai.api.quote.calculator;

import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.QuoteAccumulator;
import org.imprime.ai.api.quote.QuoteConfigReader;
import org.imprime.ai.api.quote.QuotePartCalculator;
import org.imprime.ai.api.quote.QuotePartResult;
import org.imprime.ai.api.quote.QuotingContext;
import org.imprime.ai.api.quote.ResolvedQuoteModule;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class ExtraCostsQuotePartCalculator implements QuotePartCalculator {

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.BASE_COST;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        BigDecimal defaultExtraCost = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "defaultExtraCost", BigDecimal.ZERO);

        BigDecimal rawExtraCost = context.getInput().extraCosts() != null
                ? QuoteConfigReader.sanitizeNonNegative(context.getInput().extraCosts())
                : defaultExtraCost;
        boolean perUnit = "per_unit".equalsIgnoreCase(context.getExtraCostsScope());

        BigDecimal unitCostRaw;
        BigDecimal totalCostRaw;
        if (perUnit) {
            unitCostRaw = rawExtraCost;
            totalCostRaw = rawExtraCost.multiply(BigDecimal.valueOf(context.getQuantity()));
        } else {
            totalCostRaw = rawExtraCost;
            unitCostRaw = context.unitAmount(totalCostRaw);
        }

        QuoteAccumulator acc = context.getAccumulator();
        acc.setExtraCostsTotal(totalCostRaw);
        acc.addBaseProductionCost(totalCostRaw);

        String description = context.getInput().extraCostsDescription();
        String trimmedDesc = description != null ? description.trim() : "";
        String detailLabel;
        if (perUnit && rawExtraCost.compareTo(BigDecimal.ZERO) > 0) {
            String perUnitText = QuoteConfigReader.formatBrl(rawExtraCost) + "/un × " + context.getQuantity();
            detailLabel = !trimmedDesc.isEmpty() ? trimmedDesc + " · " + perUnitText : perUnitText;
        } else {
            detailLabel = !trimmedDesc.isEmpty() ? trimmedDesc : "Custos extras por pedido";
        }

        return new QuotePartResult(
                module.code(),
                module.title(),
                module.quoteType(),
                module.executionOrder(),
                module.enabled(),
                module.overridden(),
                QuoteConfigReader.scale2(unitCostRaw),
                QuoteConfigReader.scale2(totalCostRaw),
                totalCostRaw,
                BigDecimal.ZERO.setScale(2),
                detailLabel,
                Map.of(
                        "amount", QuoteConfigReader.scale2(totalCostRaw),
                        "unitAmount", QuoteConfigReader.scale2(unitCostRaw),
                        "scope", perUnit ? "per_unit" : "per_order"
                )
        );
    }
}
