package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Slot AMS / alimentador multi-filamento vinculado à impressora")
public record AmsSlotDto(
        @Schema(description = "Número do slot", example = "1")
        int slotNumber,
        @Schema(description = "GUID do filamento carregado (opcional)")
        String filamentGuid,
        @Schema(description = "Rótulo identificador do slot", example = "Slot A1")
        String label
) {
}
