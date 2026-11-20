package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.config.PrintPricingProperties;
import org.imprime.ai.api.http.request.print.PrintEstimateRequest;
import org.imprime.ai.api.http.response.print.PrintEstimateResponse;
import org.imprime.ai.api.http.response.print.PrintPricingConfigResponse;
import org.imprime.ai.api.service.PrintPricingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrintPricingServiceImpl implements PrintPricingService {
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final PrintPricingProperties properties;

    @Override
    public PrintPricingConfigResponse getPricingConfig() {
        Map<org.imprime.ai.api.model.enums.MaterialType, BigDecimal> multipliers =
                properties.getMaterialMultipliers() != null
                        ? Collections.unmodifiableMap(properties.getMaterialMultipliers())
                        : Map.of();

        return new PrintPricingConfigResponse(
                properties.getBasePrice(),
                properties.getPricePerGram(),
                properties.getPricePerHour(),
                properties.getCurrency(),
                multipliers
        );
    }

    @Override
    public PrintEstimateResponse estimatePrint(PrintEstimateRequest request) {
        BigDecimal multiplier = properties.getMaterialMultipliers() != null
                ? properties.getMaterialMultipliers().getOrDefault(request.material(), BigDecimal.ONE)
                : BigDecimal.ONE;

        BigDecimal baseFee = properties.getBasePrice().setScale(2, RoundingMode.HALF_UP);

        BigDecimal materialCost = request.weightGrams()
                .multiply(properties.getPricePerGram())
                .multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal hours = BigDecimal.valueOf(request.printTimeMinutes())
                .divide(MINUTES_PER_HOUR, 6, RoundingMode.HALF_UP);

        BigDecimal machineTimeCost = hours
                .multiply(properties.getPricePerHour())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal unitPrice = baseFee
                .add(materialCost)
                .add(machineTimeCost)
                .setScale(2, RoundingMode.HALF_UP);

        int quantity = request.quantity() != null ? request.quantity() : 1;
        BigDecimal totalPrice = unitPrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        return new PrintEstimateResponse(
                baseFee,
                materialCost,
                machineTimeCost,
                unitPrice,
                totalPrice,
                properties.getCurrency()
        );
    }
}
