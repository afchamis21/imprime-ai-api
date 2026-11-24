package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Sobrescrita pontual de ativação ou configuração de um módulo em um orçamento")
public record QuoteModuleOverrideRequest(
        @Schema(description = "Sobrescreve ativação/desativação do módulo apenas para esta simulação", example = "true")
        Boolean enabled,

        @Schema(description = "Chaves de configuração sobrescritas apenas para esta simulação")
        Map<String, Object> config
) {
}
