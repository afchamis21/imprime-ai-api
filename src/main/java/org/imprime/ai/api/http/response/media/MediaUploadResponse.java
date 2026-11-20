package org.imprime.ai.api.http.response.media;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MediaCategory;

import java.time.Instant;

@Schema(description = "Resultado do upload de mídia para o Object Storage")
public record MediaUploadResponse(
        @Schema(description = "GUID do FileAsset registrado", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String fileAssetGuid,
        @Schema(description = "URL pública ou caminho de referência do arquivo armazenado")
        String url,
        @Schema(description = "Content-Type MIME do arquivo", example = "image/png")
        String contentType,
        @Schema(description = "Tamanho do arquivo em bytes", example = "245812")
        long fileSizeBytes,
        @Schema(description = "Categoria da mídia enviada", example = "PORTFOLIO")
        MediaCategory category,
        @Schema(description = "Data e hora do upload em UTC (ISO-8601)")
        Instant uploadedAt
) {
}
