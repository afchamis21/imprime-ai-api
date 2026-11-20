package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Nova chave de API gerada para o agente local da impressora (exibida uma única vez)")
public record RegenerateApiKeyResponse(
        @Schema(description = "GUID da impressora")
        String printerGuid,
        @Schema(description = "Nova API Key em texto claro (armazene com segurança — não será exibida novamente)", example = "iak_live_9f8e7d6c5b4a")
        String apiKey,
        @Schema(description = "Prefixo identificador da nova chave", example = "iak_live_9f8e****")
        String apiKeyPrefix,
        @Schema(description = "Data e hora de emissão em UTC (ISO-8601)")
        Instant generatedAt
) {
}
