package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo operacional em tempo real da frota de impressoras e estoque do Maker")
public record FleetSummaryDto(
        @Schema(description = "Total de impressoras cadastradas", example = "3")
        int totalPrinters,
        @Schema(description = "Quantidade de impressoras online", example = "3")
        int onlinePrinters,
        @Schema(description = "Quantidade de impressoras imprimindo no momento", example = "1")
        int printingPrinters,
        @Schema(description = "Quantidade de jobs aguardando na fila", example = "4")
        int queuedJobs,
        @Schema(description = "Quantidade de projetos multi-peça ativos", example = "3")
        int activeProjects,
        @Schema(description = "Quantidade de filamentos/resinas com estoque abaixo do mínimo", example = "2")
        int lowStockFilaments,
        @Schema(description = "Job ativo em destaque na frota (nulo se nenhuma impressora estiver imprimindo)")
        ActivePrintJobDto activeJob
) {
}
