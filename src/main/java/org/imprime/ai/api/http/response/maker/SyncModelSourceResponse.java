package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado da sincronização manual de uma fonte de modelos 3D")
public record SyncModelSourceResponse(
        @Schema(description = "Estado atualizado da fonte após a sincronização")
        ModelSourceResponse source,
        @Schema(description = "Quantidade de novos arquivos importados nesta execução", example = "1")
        int newlyImportedCount
) {
}
