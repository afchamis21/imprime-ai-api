package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.imprime.ai.api.model.enums.QueuePriority;

@Schema(description = "Atualização parcial de um job da fila (impressora designada, prioridade, cópias)")
public record UpdatePrintQueueItemRequest(
        @Schema(description = "GUID da nova impressora designada (opcional)")
        String printerGuid,
        @Schema(description = "Se `true`, remove a impressora designada do job (somente jobs não iniciados)")
        Boolean unassignPrinter,
        @Schema(description = "Nova prioridade do job", example = "URGENT")
        QueuePriority priority,
        @Schema(description = "Nova quantidade de cópias", example = "2")
        @Min(1) @Max(500)
        Integer copies
) {
}
