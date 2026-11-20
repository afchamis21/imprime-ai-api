package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Volume útil da mesa de impressão em milímetros")
public record BuildVolumeDto(
        @Schema(description = "Eixo X em mm", example = "256")
        int xMm,
        @Schema(description = "Eixo Y em mm", example = "256")
        int yMm,
        @Schema(description = "Eixo Z em mm", example = "256")
        int zMm
) {
}
