package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.ModelSourceProvider;
import org.imprime.ai.api.model.enums.ModelSourceStatus;

import java.time.Instant;
import java.util.List;

@Schema(description = "Conector de repositório de modelos 3D configurado pelo Maker")
public record ModelSourceResponse(
        @Schema(description = "GUID único da integração")
        String guid,
        @Schema(description = "Nome da integração", example = "Catálogo Imprime AI")
        String name,
        @Schema(description = "Provedor", example = "IMPRIME_AI")
        ModelSourceProvider provider,
        @Schema(description = "URL ou caminho configurado")
        String url,
        @Schema(description = "Descrição do conector")
        String description,
        @Schema(description = "Indica se a integração está habilitada", example = "true")
        boolean enabled,
        @Schema(description = "Indica se a sincronização automática está ativa", example = "true")
        boolean autoSync,
        @Schema(description = "Intervalo entre sincronizações em horas", example = "1")
        int syncIntervalHours,
        @Schema(description = "Status operacional atual da fonte", example = "CONNECTED")
        ModelSourceStatus status,
        @Schema(description = "Instante da última sincronização em UTC (ISO-8601)")
        Instant lastSyncedAt,
        @Schema(description = "Total de modelos mapeados na fonte", example = "38")
        int modelsCount,
        @Schema(description = "Total de arquivos importados no Print Archive", example = "19")
        int importedFilesCount,
        @Schema(description = "Indica se há um token de API configurado (o segredo nunca é exposto)", example = "true")
        boolean apiTokenConfigured,
        @Schema(description = "Tags associadas")
        List<String> tags
) {
}
