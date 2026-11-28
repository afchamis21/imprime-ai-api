package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum PrinterSpeedTier {
    HIGH_SPEED(new BigDecimal("1.00"), "Alta Velocidade (1.0x)"),
    MEDIUM_SPEED(new BigDecimal("1.40"), "Média Velocidade (1.4x)"),
    STANDARD(new BigDecimal("2.20"), "Padrão (2.2x)");

    private final BigDecimal defaultMultiplier;
    private final String label;

    public static PrinterSpeedTier fromMultiplier(BigDecimal multiplier) {
        if (multiplier == null) {
            return STANDARD;
        }
        if (multiplier.compareTo(new BigDecimal("1.15")) <= 0) {
            return HIGH_SPEED;
        }
        if (multiplier.compareTo(new BigDecimal("1.75")) <= 0) {
            return MEDIUM_SPEED;
        }
        return STANDARD;
    }
}
