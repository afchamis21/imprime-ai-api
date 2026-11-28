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
import org.imprime.ai.api.model.enums.InstantQuoteSortBy;

import java.math.BigDecimal;
import java.util.List;

public record InstantPrintQuoteRequest(
        @Size(max = 36) String addressGuid,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("1.0") @DecimalMax("500.0") Double maxDistanceKm,
        @Min(1) @Max(100) Integer maxResults,
        InstantQuoteSortBy sortBy,
        @Size(max = 30) String slicerEngineId,
        @Size(max = 120) String slicerProfileId,
        @Valid @NotEmpty @Size(max = 20) List<PlateDnaInput> plates
) {
    public record BoundingBoxMmInput(
            @NotNull @DecimalMin("0.1") @DecimalMax("2000.0") BigDecimal x,
            @NotNull @DecimalMin("0.1") @DecimalMax("2000.0") BigDecimal y,
            @NotNull @DecimalMin("0.1") @DecimalMax("2000.0") BigDecimal z
    ) {
    }

    public record PlateDnaInput(
            @Min(1) Integer plateNumber,
            @Size(max = 255) String plateName,
            @NotBlank @Size(max = 30) String material,
            @NotNull @Min(1) @Max(1000) Integer quantity,
            @NotNull @DecimalMin("0.01") @DecimalMax("50000.00") BigDecimal modelWeightGrams,
            @DecimalMin("0.00") @DecimalMax("50000.00") BigDecimal supportWeightGrams,
            @NotNull @Min(1) @Max(10_000_000) Integer basePrintTimeSeconds,
            @Valid @NotNull BoundingBoxMmInput boundingBoxMm,
            @DecimalMin("0.04") @DecimalMax("1.00") BigDecimal layerHeightMm,
            @Min(0) @Max(100) Integer infillPercent,
            @Size(max = 30) String infillPattern,
            @Min(1) @Max(20) Integer wallCount,
            Boolean supports,
            @Size(max = 30) String supportType,
            @Size(max = 30) String adhesionType,
            @Size(max = 30) String finish,
            @Size(max = 30) String urgency,
            Boolean isMultiColor,
            @Min(1) @Max(16) Integer colorCount
    ) {
    }
}
