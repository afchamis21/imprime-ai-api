package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.ModelSourceStatus;

import java.util.List;

@Schema(description = "Dados para atualização parcial (PATCH) de uma fonte de modelos 3D")
public record UpdateModelSourceRequest(
        @Schema(description = "Nome atualizado")
        @Size(min = 1, max = 120, message = "O nome deve ter entre 1 e 120 caracteres")
        String name,

        @Schema(description = "URL ou caminho atualizado")
        @Size(min = 1, max = 500, message = "A URL deve ter entre 1 e 500 caracteres")
        String url,

        @Schema(description = "Descrição atualizada")
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        String description,

        @Schema(description = "Ativa ou desativa a integração")
        Boolean enabled,

        @Schema(description = "Ativa ou desativa sincronização automática")
        Boolean autoSync,

        @Schema(description = "Intervalo de sincronização em horas")
        @Min(value = 1, message = "O intervalo mínimo de sincronização é 1 hora")
        Integer syncIntervalHours,

        @Schema(description = "Status explícito da integração")
        ModelSourceStatus status,

        @Schema(description = "Tags atualizadas")
        @Size(max = 10, message = "Máximo de 10 tags")
        List<String> tags,

        @Schema(description = "Novo token de API (write-only)")
        @Size(max = 500, message = "O token deve ter no máximo 500 caracteres")
        String apiToken
) {
}
