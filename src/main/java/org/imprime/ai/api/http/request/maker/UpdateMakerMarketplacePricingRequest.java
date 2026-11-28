package org.imprime.ai.api.http.request.maker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.SetupFeeScope;

import java.math.BigDecimal;
import java.util.List;

public record UpdateMakerMarketplacePricingRequest(
        Boolean instantQuoteEnabled,
        @NotNull @DecimalMin("0.00") @DecimalMax("500.00") BigDecimal hourlyRateBrl,
        @NotNull @DecimalMin("0.00") @DecimalMax("5000.00") BigDecimal minimumOrderFeeBrl,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") BigDecimal setupFeeBrl,
        @NotNull SetupFeeScope setupFeeScope,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") BigDecimal supportRemovalFeeBrl,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal supportPricePerGramBrl,
        @NotNull @Min(1) @Max(90) Integer leadTimeDays,
        @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal expressMultiplier,
        @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal finishSandedMultiplier,
        @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal finishVaporMultiplier,
        @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal finishPaintedMultiplier,
        @Valid @NotNull @Size(max = 30) List<MaterialPriceInput> materialPrices
) {
    public record MaterialPriceInput(
            @NotNull MaterialType material,
            @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal pricePerGramBrl,
            Boolean enabled,
            @Size(max = 200) String notes
    ) {
    }
}
