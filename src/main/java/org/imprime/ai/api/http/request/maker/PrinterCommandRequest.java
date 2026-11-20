package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.imprime.ai.api.model.enums.PrinterCommandAction;

@Schema(description = "Comando operacional remoto enviado para uma impressora")
public record PrinterCommandRequest(
        @Schema(description = "Ação a ser executada pela impressora", example = "PAUSE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O comando operacional é obrigatório")
        PrinterCommandAction action
) {
}
