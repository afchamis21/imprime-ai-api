package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;

@Schema(description = "Atualização modular dos parâmetros fixos de orçamento do estúdio")
public record UpdateMakerQuoteModulesRequest(
        @Schema(description = "Lista de módulos de cálculo configurados para o estúdio", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "A lista de módulos é obrigatória")
        @Valid
        List<MakerQuoteModuleUpdateItem> modules
) {
    @Schema(description = "Configuração individual de um módulo do catálogo de orçamento")
    public record MakerQuoteModuleUpdateItem(
            @Schema(description = "Código único do módulo no catálogo", example = "POWER", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "O código do módulo é obrigatório")
            String code,

            @Schema(description = "Define se o módulo está ativo no pipeline padrão do estúdio", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "O estado habilitado do módulo é obrigatório")
            Boolean enabled,

            @Schema(description = "Ordem de execução opcional dentro da fase do módulo", example = "20")
            @PositiveOrZero(message = "A ordem de execução não pode ser negativa")
            Integer executionOrder,

            @Schema(description = "Parâmetros específicos do módulo em JSON")
            Map<String, Object> config
    ) {
    }
}
