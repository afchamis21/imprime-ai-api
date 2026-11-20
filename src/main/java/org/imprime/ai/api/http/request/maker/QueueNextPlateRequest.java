package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.QueuePriority;

@Schema(description = "Parâmetros opcionais ao enfileirar a próxima mesa de um projeto")
public record QueueNextPlateRequest(
        @Schema(description = "GUID da impressora alvo (se omitido, seleciona impressora disponível)")
        String printerGuid,

        @Schema(description = "Prioridade do job gerado na fila (padrão HIGH)", example = "HIGH")
        QueuePriority priority
) {
}
