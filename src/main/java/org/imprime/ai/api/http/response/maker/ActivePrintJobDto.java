package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Job atualmente em execução exibido no resumo da frota do Maker")
public record ActivePrintJobDto(
        @Schema(description = "GUID do job na fila")
        String jobGuid,
        @Schema(description = "GUID da impressora")
        String printerGuid,
        @Schema(description = "Nome da impressora", example = "X1C Principal")
        String printerName,
        @Schema(description = "Título do job em impressão", example = "Protótipo Arquitetônico — PED-487")
        String jobTitle,
        @Schema(description = "Progresso percentual (0 a 100)", example = "68")
        int progressPercent,
        @Schema(description = "Tempo restante estimado em minutos", example = "84")
        int etaMinutes
) {
}
