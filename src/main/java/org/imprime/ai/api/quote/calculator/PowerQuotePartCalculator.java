package org.imprime.ai.api.quote.calculator;

import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.QuoteAccumulator;
import org.imprime.ai.api.quote.QuoteConfigReader;
import org.imprime.ai.api.quote.QuoteInput;
import org.imprime.ai.api.quote.QuotePartCalculator;
import org.imprime.ai.api.quote.QuotePartResult;
import org.imprime.ai.api.quote.QuoteValidationWarning;
import org.imprime.ai.api.quote.QuotingContext;
import org.imprime.ai.api.quote.ResolvedQuoteModule;
import org.imprime.ai.api.service.impl.MakerFinanceServiceImpl;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PowerQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_POWER_WATTS = new BigDecimal("300");

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.BASE_COST;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        BigDecimal kwhRate = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "kwhRate", MakerFinanceServiceImpl.DEFAULT_KWH_RATE);
        BigDecimal defaultWatts = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "defaultPowerWatts", DEFAULT_POWER_WATTS);

        QuoteAccumulator acc = context.getAccumulator();
        QuoteInput.PrinterInput printerInput = context.getInput().printer();
        String mode = printerInput != null && printerInput.mode() != null
                ? printerInput.mode().trim()
                : "select";

        BigDecimal effectiveWatts;
        String powerSource;

        if ("select".equalsIgnoreCase(mode)) {
            Asset selectedPrinter = printerInput != null
                    ? context.findPrinter(printerInput.printerId())
                    : null;
            if (selectedPrinter != null
                    && selectedPrinter.getPowerWatts() != null
                    && selectedPrinter.getPowerWatts() > 0) {
                effectiveWatts = BigDecimal.valueOf(selectedPrinter.getPowerWatts());
                powerSource = "printer";
            } else {
                effectiveWatts = defaultWatts;
                powerSource = "fallback";
                if (selectedPrinter != null) {
                    acc.addWarningOnce(new QuoteValidationWarning(
                            "PRINTER_FALLBACK_WATTS",
                            "info",
                            "Impressora selecionada sem consumo cadastrado; usando padrão de "
                                    + QuoteConfigReader.formatCompactPtBr(defaultWatts, 0) + "W."
                    ));
                }
            }
        } else {
            BigDecimal manualWatts = printerInput != null ? printerInput.manualPowerWatts() : null;
            if (manualWatts != null && manualWatts.compareTo(BigDecimal.ZERO) >= 0) {
                effectiveWatts = manualWatts;
                powerSource = "manual";
            } else {
                effectiveWatts = defaultWatts;
                powerSource = "fallback";
            }
        }

        if (context.getTotalPrintHours().compareTo(BigDecimal.ZERO) <= 0) {
            acc.addWarningOnce(new QuoteValidationWarning(
                    "ZERO_PRINT_TIME",
                    "info",
                    "Informe o tempo estimado de impressão para calcular energia e depreciação."
            ));
        }

        BigDecimal totalEnergyKwh = QuoteConfigReader.divide(effectiveWatts, QuoteConfigReader.THOUSAND)
                .multiply(context.getTotalPrintHours());
        BigDecimal unitEnergyKwh = context.unitAmount(totalEnergyKwh);
        BigDecimal totalCostRaw = totalEnergyKwh.multiply(kwhRate);
        BigDecimal unitCostRaw = context.unitAmount(totalCostRaw);

        acc.setPowerWatts(effectiveWatts);
        acc.setPowerSource(powerSource);
        acc.setTotalEnergyKwh(totalEnergyKwh);
        acc.setUnitEnergyKwh(unitEnergyKwh);
        acc.setEnergyCostTotal(totalCostRaw);
        acc.addDirectPrintCost(totalCostRaw);

        String detailLabel = QuoteConfigReader.formatCompactPtBr(totalEnergyKwh, 3)
                + " kWh · "
                + QuoteConfigReader.formatCompactPtBr(effectiveWatts, 0)
                + "W ("
                + QuoteConfigReader.formatBrl(kwhRate)
                + "/kWh)";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("totalKwh", QuoteConfigReader.scale(totalEnergyKwh, 3));
        metadata.put("unitKwh", QuoteConfigReader.scale(unitEnergyKwh, 3));
        metadata.put("powerWatts", QuoteConfigReader.scale2(effectiveWatts));
        metadata.put("kwhRate", QuoteConfigReader.scale(kwhRate, 4));
        metadata.put("powerSource", powerSource);

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
