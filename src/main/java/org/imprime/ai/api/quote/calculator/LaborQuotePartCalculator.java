package org.imprime.ai.api.quote.calculator;

import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.QuoteAccumulator;
import org.imprime.ai.api.quote.QuoteConfigReader;
import org.imprime.ai.api.quote.QuotePartCalculator;
import org.imprime.ai.api.quote.QuotePartResult;
import org.imprime.ai.api.quote.QuoteValidationWarning;
import org.imprime.ai.api.quote.QuotingContext;
import org.imprime.ai.api.quote.ResolvedQuoteModule;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LaborQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_LABOR_HOURLY_RATE = new BigDecimal("35.00");
    private static final int DEFAULT_LABOR_MINUTES = 15;

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.BASE_COST;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        BigDecimal laborHourlyRate = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "laborHourlyRate", DEFAULT_LABOR_HOURLY_RATE);
        int defaultLaborMinutes = Math.max(0, QuoteConfigReader.getInt(
                effectiveConfig, "defaultLaborMinutes", DEFAULT_LABOR_MINUTES));
        boolean requireExplicitToggle = QuoteConfigReader.getBoolean(
                effectiveConfig, "requireExplicitToggleOnQuote", true);

        QuoteAccumulator acc = context.getAccumulator();
        boolean requiresLabor = Boolean.TRUE.equals(context.getInput().requiresLabor());

        if (requireExplicitToggle && !requiresLabor) {
            acc.setRequiresLabor(false);
            acc.setEffectiveLaborTimeMinutes(0);
            acc.setLaborCostTotal(BigDecimal.ZERO);

            return new QuotePartResult(
                    module.code(),
                    module.title(),
                    module.quoteType(),
                    module.executionOrder(),
                    module.enabled(),
                    module.overridden(),
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO.setScale(2),
                    "Não aplicável",
                    Map.of("status", "SKIPPED_BY_QUOTE", "minutes", 0)
            );
        }

        BigDecimal minutes = context.getInput().laborTimeMinutes() != null
                ? QuoteConfigReader.sanitizeNonNegative(context.getInput().laborTimeMinutes())
                : BigDecimal.valueOf(defaultLaborMinutes);

        if (minutes.compareTo(BigDecimal.ZERO) <= 0) {
            acc.addWarningOnce(new QuoteValidationWarning(
                    "LABOR_ENABLED_WITH_ZERO_TIME",
                    "info",
                    "Mão de obra ativada, mas o tempo informado está zerado."
            ));
        }

        BigDecimal totalCostRaw = QuoteConfigReader.divide(minutes, QuoteConfigReader.SIXTY)
                .multiply(laborHourlyRate);
        BigDecimal unitCostRaw = context.unitAmount(totalCostRaw);
        int roundedMinutes = minutes.setScale(0, RoundingMode.HALF_UP).intValue();

        acc.setRequiresLabor(requiresLabor);
        acc.setEffectiveLaborTimeMinutes(roundedMinutes);
        acc.setLaborCostTotal(totalCostRaw);
        acc.addBaseProductionCost(totalCostRaw);

        String notes = context.getInput().laborNotes();
        String detailLabel = notes != null && !notes.isBlank()
                ? roundedMinutes + "min · " + notes.trim()
                : roundedMinutes + "min · " + QuoteConfigReader.formatBrl(laborHourlyRate) + "/h";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "APPLIED");
        metadata.put("minutes", roundedMinutes);
        metadata.put("hourlyRate", QuoteConfigReader.scale2(laborHourlyRate));

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
                Map.copyOf(metadata)
        );
    }
}
