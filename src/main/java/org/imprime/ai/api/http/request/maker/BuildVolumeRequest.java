package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Volume útil de impressão em milímetros")
public record BuildVolumeRequest(
        @Schema(description = "Dimensão X em mm", example = "256", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A dimensão X é obrigatória")
        @Positive(message = "A dimensão X deve ser positiva")
        Integer xMm,

        @Schema(description = "Dimensão Y em mm", example = "256", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A dimensão Y é obrigatória")
        @Positive(message = "A dimensão Y deve ser positiva")
        Integer yMm,

        @Schema(description = "Dimensão Z em mm", example = "256", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A dimensão Z é obrigatória")
        @Positive(message = "A dimensão Z deve ser positiva")
        Integer zMm
) {
}
