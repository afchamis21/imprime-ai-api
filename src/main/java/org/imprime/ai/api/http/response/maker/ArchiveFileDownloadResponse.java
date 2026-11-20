package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "URL pré-assinada temporária para download do arquivo do Print Archive")
public record ArchiveFileDownloadResponse(
        @Schema(description = "GUID do arquivo no acervo")
        String fileGuid,
        @Schema(description = "Nome do arquivo para download")
        String fileName,
        @Schema(description = "URL pré-assinada temporária no Object Storage")
        String downloadUrl,
        @Schema(description = "Instante de expiração da URL em UTC (ISO-8601)")
        Instant expiresAt
) {
}
