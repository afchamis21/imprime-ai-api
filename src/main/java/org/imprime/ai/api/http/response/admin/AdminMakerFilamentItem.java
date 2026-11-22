package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Schema(description = "Resumo de filamento/material cadastrado no estúdio do Maker")
public record AdminMakerFilamentItem(
        String guid,
        String name,
        String brand,
        MaterialType material,
        String colorName,
        String colorHex,
        BigDecimal stock,
        BigDecimal costPerKg,
        BigDecimal diameterMm
) {
}
