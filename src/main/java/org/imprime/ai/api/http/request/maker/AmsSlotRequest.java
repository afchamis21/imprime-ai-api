package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Configuração de um slot AMS / multi-filamento")
public record AmsSlotRequest(
        @Schema(description = "Número do slot (1 a 16)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O número do slot é obrigatório")
        @Min(value = 1, message = "O número do slot deve ser maior ou igual a 1")
        Integer slotNumber,

        @Schema(description = "GUID do rolo de filamento carregado no slot (opcional)")
        String filamentGuid,

        @Schema(description = "Rótulo operacional do slot", example = "Slot A1")
        @Size(max = 50, message = "O rótulo do slot deve ter no máximo 50 caracteres")
        String label
) {
}
