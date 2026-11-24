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
public class DepreciationQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_MACHINE_PRICE = new BigDecimal("3500.00");
    private static final BigDecimal DEFAULT_MAINTENANCE_HOURLY = new BigDecimal("0.50");
    private static final int DEFAULT_LIFESPAN_HOURS = 3000;

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.BASE_COST;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        String depreciationMode = QuoteConfigReader.getString(effectiveConfig, "depreciationMode", "FIXED_HOURLY");
        BigDecimal settingsHourlyRate = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "hourlyDepreciationRate", MakerFinanceServiceImpl.DEFAULT_HOURLY_DEPRECIATION_RATE);
        BigDecimal machinePrice = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "defaultMachinePrice", DEFAULT_MACHINE_PRICE);
        int lifespanHours = Math.max(0, QuoteConfigReader.getInt(
                effectiveConfig, "defaultMachineLifespanHours", DEFAULT_LIFESPAN_HOURS));
        BigDecimal maintenanceHourly = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "defaultMaintenanceHourlyCost", DEFAULT_MAINTENANCE_HOURLY);

        QuoteAccumulator acc = context.getAccumulator();
        if (context.getTotalPrintHours().compareTo(BigDecimal.ZERO) <= 0) {
            acc.addWarningOnce(new QuoteValidationWarning(
                    "ZERO_PRINT_TIME",
                    "info",
                    "Informe o tempo estimado de impressão para calcular energia e depreciação."
            ));
        }

        ResolvedRate resolved = resolveHourlyRate(
                context,
                depreciationMode,
                settingsHourlyRate,
                machinePrice,
                lifespanHours,
                maintenanceHourly
        );

        BigDecimal totalCostRaw = context.getTotalPrintHours().multiply(resolved.hourlyRate);
        BigDecimal unitCostRaw = context.unitAmount(totalCostRaw);

        acc.setHourlyDepreciationRate(resolved.hourlyRate);
        acc.setDepreciationSource(resolved.source);
        acc.setDepreciationCostTotal(totalCostRaw);
        acc.addDirectPrintCost(totalCostRaw);

        String detailLabel = QuoteConfigReader.formatDuration(context.getTotalPrintHours())
                + " · "
                + QuoteConfigReader.formatBrl(resolved.hourlyRate)
                + "/h";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("hourlyRate", QuoteConfigReader.scale2(resolved.hourlyRate));
        metadata.put("source", resolved.source);
        metadata.put("totalPrintHours", QuoteConfigReader.scale(context.getTotalPrintHours(), 4));

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

    private ResolvedRate resolveHourlyRate(
            QuotingContext context,
            String depreciationMode,
            BigDecimal settingsHourlyRate,
            BigDecimal machinePrice,
            int lifespanHours,
            BigDecimal maintenanceHourly) {
        QuoteInput.PrinterInput printerInput = context.getInput().printer();
        String mode = printerInput != null && printerInput.mode() != null
                ? printerInput.mode().trim()
                : "select";

        if ("select".equalsIgnoreCase(mode) && printerInput != null) {
            Asset selectedPrinter = context.findPrinter(printerInput.printerId());
            if (selectedPrinter != null) {
                if (selectedPrinter.getHourlyDepreciationRate() != null
                        && selectedPrinter.getHourlyDepreciationRate().compareTo(BigDecimal.ZERO) > 0) {
                    return new ResolvedRate(selectedPrinter.getHourlyDepreciationRate(), "printer_rate");
                }
                if (selectedPrinter.getPurchasePrice() != null
                        && selectedPrinter.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0
                        && selectedPrinter.getLifespanHours() != null
                        && selectedPrinter.getLifespanHours() > 0) {
                    BigDecimal perHour = QuoteConfigReader.divide(
                            selectedPrinter.getPurchasePrice(),
                            BigDecimal.valueOf(selectedPrinter.getLifespanHours())
                    ).add(maintenanceHourly);
                    return new ResolvedRate(perHour, "printer_lifecycle");
                }
                return resolveSettingsFallback(
                        depreciationMode, settingsHourlyRate, machinePrice, lifespanHours, maintenanceHourly);
            }
        }

        if ("manual".equalsIgnoreCase(mode)
                && printerInput != null
                && printerInput.manualHourlyDepreciationRate() != null
                && printerInput.manualHourlyDepreciationRate().compareTo(BigDecimal.ZERO) >= 0) {
            return new ResolvedRate(printerInput.manualHourlyDepreciationRate(), "manual");
        }

        return resolveSettingsFallback(
                depreciationMode, settingsHourlyRate, machinePrice, lifespanHours, maintenanceHourly);
    }

    private ResolvedRate resolveSettingsFallback(
            String depreciationMode,
            BigDecimal settingsHourlyRate,
            BigDecimal machinePrice,
            int lifespanHours,
            BigDecimal maintenanceHourly) {
        if ("MACHINE_LIFECYCLE".equalsIgnoreCase(depreciationMode) && lifespanHours > 0) {
            BigDecimal perHour = QuoteConfigReader.divide(
                    machinePrice,
                    BigDecimal.valueOf(lifespanHours)
            ).add(maintenanceHourly);
            return new ResolvedRate(perHour, "settings_lifecycle");
        }
        return new ResolvedRate(settingsHourlyRate, "settings_rate");
    }

    private record ResolvedRate(BigDecimal hourlyRate, String source) {
    }
}
