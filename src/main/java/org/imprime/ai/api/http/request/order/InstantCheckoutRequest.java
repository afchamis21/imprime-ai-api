package org.imprime.ai.api.http.request.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record InstantCheckoutRequest(
        @NotBlank @Size(max = 36) String makerGuid,
        @NotBlank @Size(max = 36) String addressGuid,
        @NotNull Boolean rightsDeclared,
        @NotBlank @Size(max = 40) String declarationVersion,
        @NotBlank @Size(max = 40) String prohibitedItemsVersion,
        @Size(max = 30) String slicerEngineId,
        @Size(max = 120) String slicerProfileId,
        @DecimalMin("0.00") @DecimalMax("500000.00") BigDecimal expectedTotalPriceBrl,
        @Valid @NotEmpty @Size(max = 20) List<PlateCheckoutInput> plates
) {
    public record PlateCheckoutInput(
            @Min(1) Integer plateNumber,
            @Size(max = 255) String plateName,
            @NotBlank @Size(max = 30) String material,
            @Size(max = 30) String colorHex,
            @Size(max = 16) List<String> colors,
            @NotNull @Min(1) @Max(1000) Integer quantity,
            Double scaleX,
            Double scaleY,
            Double scaleZ,
            BigDecimal dimensionXMm,
            BigDecimal dimensionYMm,
            BigDecimal dimensionZMm,
            BigDecimal layerHeightMm,
            Integer infillPercent,
            @Size(max = 30) String infillPattern,
            Integer wallCount,
            Boolean supports,
            @Size(max = 30) String supportType,
            @Size(max = 30) String adhesionType,
            @Size(max = 30) String finish,
            @Size(max = 30) String tolerance,
            @Size(max = 30) String urgency,
            Boolean isMultiColor,
            @Min(1) @Max(16) Integer colorCount,
            @Size(max = 1000) String notes,
            @NotNull @DecimalMin("0.01") @DecimalMax("50000.00") BigDecimal clientModelWeightGrams,
            @DecimalMin("0.00") @DecimalMax("50000.00") BigDecimal clientSupportWeightGrams,
            @NotNull @Min(1) @Max(10_000_000) Integer clientBasePrintTimeSeconds
    ) {
    }
}
