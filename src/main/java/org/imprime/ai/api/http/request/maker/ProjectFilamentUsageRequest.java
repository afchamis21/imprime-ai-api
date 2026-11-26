package org.imprime.ai.api.http.request.maker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProjectFilamentUsageRequest(
        @NotBlank @Size(max = 64) String filamentGuid,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal grams,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal gramsPerPlate
) {
}
