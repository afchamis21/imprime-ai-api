package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import tools.jackson.databind.JsonNode;

@Schema(description = "Configuração efetiva de um módulo da calculadora de orçamento do Maker")
public record MakerQuoteModuleConfigResponse(
        @Schema(description = "Código único do módulo", example = "POWER")
        String code,
        @Schema(description = "Título exibido na interface", example = "Energia Elétrica")
        String title,
        @Schema(description = "Descrição funcional do módulo")
        String description,
        @Schema(description = "Fase/tipo do pipeline de cálculo", example = "BASE_COST")
        QuoteModuleType quoteType,
        @Schema(description = "Ordem da fase de execução", example = "10")
        int phaseOrder,
        @Schema(description = "Ordem de execução dentro da fase", example = "20")
        int executionOrder,
        @Schema(description = "Indica se o módulo está habilitado", example = "true")
        boolean enabled,
        @Schema(description = "Indica se o usuário pode desativar o módulo", example = "true")
        boolean canDisable,
        @Schema(description = "Indica se o módulo permite sobrescrita pontual por orçamento", example = "true")
        boolean allowQuoteOverride,
        @Schema(description = "Grupo de exclusão mútua (quando aplicável)", example = "PROFIT_STRATEGY")
        String mutualExclusionGroup,
        @Schema(description = "Configuração efetiva mesclada em JSON")
        JsonNode config,
        @Schema(description = "Metadados e campos para renderização dinâmica da UI")
        JsonNode uiSchema
) {
}
