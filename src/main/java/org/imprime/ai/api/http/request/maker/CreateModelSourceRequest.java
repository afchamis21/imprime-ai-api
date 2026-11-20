package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.ModelSourceProvider;

import java.util.List;

@Schema(description = "Dados para conectar uma nova fonte/repositório de modelos 3D ao estúdio")
public record CreateModelSourceRequest(
        @Schema(description = "Nome da integração", example = "Repositório Printables Estúdio", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome da integração é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String name,

        @Schema(description = "Provedor da fonte de modelos", example = "PRINTABLES", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O provedor é obrigatório")
        ModelSourceProvider provider,

        @Schema(description = "URL do perfil/coleção ou caminho local/SMB", example = "https://www.printables.com/@Studio3D", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A URL ou caminho da fonte é obrigatório")
        @Size(max = 500, message = "A URL deve ter no máximo 500 caracteres")
        String url,

        @Schema(description = "Descrição da integração")
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        String description,

        @Schema(description = "Habilita sincronização automática periódica (padrão true)", example = "true")
        Boolean autoSync,

        @Schema(description = "Intervalo de sincronização em horas (padrão 12)", example = "12")
        @Min(value = 1, message = "O intervalo mínimo de sincronização é 1 hora")
        Integer syncIntervalHours,

        @Schema(description = "Tags aplicadas aos modelos importados")
        @Size(max = 10, message = "Máximo de 10 tags")
        List<String> tags,

        @Schema(description = "Token/credencial opcional de API do provedor (write-only; nunca retornado em consultas)")
        @Size(max = 500, message = "O token deve ter no máximo 500 caracteres")
        String apiToken
) {
}
