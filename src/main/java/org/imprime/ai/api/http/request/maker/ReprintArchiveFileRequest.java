package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import org.imprime.ai.api.model.enums.QueuePriority;

@Schema(description = "Parâmetros opcionais para reimpressão direta de um arquivo do acervo")
public record ReprintArchiveFileRequest(
        @Schema(description = "GUID da impressora alvo (se omitido, o backend seleciona uma impressora elegível)")
        String printerGuid,

        @Schema(description = "Quantidade de cópias (padrão 1)", example = "1")
        @Min(value = 1, message = "A quantidade mínima de cópias é 1")
        Integer copies,

        @Schema(description = "Prioridade na fila (padrão NORMAL)", example = "NORMAL")
        QueuePriority priority,

        @Schema(description = "GUID do pedido associado (opcional)")
        String orderGuid
) {
}
