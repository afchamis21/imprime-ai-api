package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.FilamentFinishType;
import org.imprime.ai.api.model.enums.HygroscopicLevel;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.SpoolType;

import java.math.BigDecimal;

@Schema(description = "Payload administrativo para cadastro ou atualização completa de perfil de filamento no catálogo")
public record UpsertFilamentCatalogRequest(
        @NotBlank(message = "A marca é obrigatória")
        @Size(max = 100, message = "A marca deve ter no máximo 100 caracteres")
        String brand,

        @NotBlank(message = "A linha do filamento é obrigatória")
        @Size(max = 120, message = "A linha deve ter no máximo 120 caracteres")
        String lineName,

        @NotNull(message = "O material é obrigatório")
        MaterialType material,

        @Size(max = 60, message = "O subtipo do material deve ter no máximo 60 caracteres")
        String materialSubtype,

        @NotBlank(message = "O nome da cor é obrigatório")
        @Size(max = 80, message = "O nome da cor deve ter no máximo 80 caracteres")
        String colorName,

        @NotBlank(message = "O código hexadecimal da cor é obrigatório")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor hexadecimal inválida (use #RRGGBB)")
        String colorHex,

        @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "Cor secundária hexadecimal inválida (use #RRGGBB)")
        String secondaryColorHex,

        FilamentFinishType finishType,

        @PositiveOrZero(message = "O diâmetro não pode ser negativo")
        BigDecimal diameterMm,

        @Positive(message = "A densidade deve ser positiva")
        BigDecimal densityGCm3,

        @Positive(message = "O peso padrão do carretel deve ser positivo")
        Integer defaultSpoolWeightG,

        SpoolType spoolType,

        @NotNull(message = "A temperatura mínima do bico é obrigatória")
        @PositiveOrZero(message = "A temperatura mínima do bico não pode ser negativa")
        Integer nozzleTempMinC,

        @NotNull(message = "A temperatura máxima do bico é obrigatória")
        @PositiveOrZero(message = "A temperatura máxima do bico não pode ser negativa")
        Integer nozzleTempMaxC,

        @PositiveOrZero(message = "A temperatura recomendada do bico não pode ser negativa")
        Integer nozzleTempRecommendedC,

        @NotNull(message = "A temperatura mínima da mesa é obrigatória")
        @PositiveOrZero(message = "A temperatura mínima da mesa não pode ser negativa")
        Integer bedTempMinC,

        @NotNull(message = "A temperatura máxima da mesa é obrigatória")
        @PositiveOrZero(message = "A temperatura máxima da mesa não pode ser negativa")
        Integer bedTempMaxC,

        @PositiveOrZero(message = "A temperatura recomendada da mesa não pode ser negativa")
        Integer bedTempRecommendedC,

        @Positive(message = "O fluxo volumétrico máximo deve ser positivo")
        BigDecimal maxVolumetricSpeedMm3S,

        @PositiveOrZero(message = "O fator Pressure Advance K não pode ser negativo")
        BigDecimal pressureAdvanceK,

        @PositiveOrZero(message = "A distância de transmissão TD não pode ser negativa")
        BigDecimal transmissionDistanceMm,

        @PositiveOrZero(message = "A temperatura de secagem não pode ser negativa")
        Integer dryingTempC,

        @PositiveOrZero(message = "O tempo de secagem não pode ser negativo")
        Integer dryingTimeHours,

        Boolean requiresEnclosure,

        Boolean requiresHardenedNozzle,

        Boolean amsCompatible,

        HygroscopicLevel hygroscopicLevel,

        @Size(max = 15, message = "A dureza Shore deve ter no máximo 15 caracteres")
        String shoreHardness,

        @PositiveOrZero(message = "O preço de referência por kg não pode ser negativo")
        BigDecimal referencePriceBrlPerKg,

        @Size(max = 80, message = "O SKU deve ter no máximo 80 caracteres")
        String sku,

        @Size(max = 32, message = "O código GTIN/EAN deve ter no máximo 32 caracteres")
        String gtinEan,

        @Size(max = 500, message = "A URL de compra deve ter no máximo 500 caracteres")
        String purchaseUrl,

        @Size(max = 1000, message = "A URL da imagem deve ter no máximo 1000 caracteres")
        String imageUrl,

        String notes,

        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
        String reason
) {
}
