package org.imprime.ai.api.http.response.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "URL pré-assinada temporária e auditoria de download do modelo 3D comprado")
public record ModelDownloadResponse(
        @Schema(description = "GUID da compra", example = "11111111-2222-3333-4444-555555555555")
        String purchaseGuid,
        @Schema(description = "GUID do modelo 3D", example = "7f9c24ef-1000-4000-8000-000000000001")
        String modelGuid,
        @Schema(description = "URL pré-assinada temporária para download direto do binário")
        String downloadUrl,
        @Schema(description = "Instante de expiração da URL pré-assinada em UTC (ISO-8601)")
        Instant expiresAt,
        @Schema(description = "Contagem atualizada de downloads realizados", example = "4")
        int downloadsCount
) {
}
