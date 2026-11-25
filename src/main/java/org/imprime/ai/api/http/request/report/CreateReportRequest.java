package org.imprime.ai.api.http.request.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportTargetType;

import java.util.List;

@Schema(description = "Requisição para envio de denúncia sobre produto, modelo 3D ou maker")
public record CreateReportRequest(
        @NotNull(message = "O tipo do alvo é obrigatório")
        @Schema(description = "Tipo da entidade denunciada (PRODUCT, MODEL_3D, MAKER)")
        ReportTargetType targetType,

        @NotBlank(message = "O identificador do alvo é obrigatório")
        @Size(max = 180, message = "O identificador do alvo deve ter no máximo 180 caracteres")
        @Schema(description = "GUID ou slug do produto, modelo 3D ou maker")
        String targetIdOrSlug,

        @NotNull(message = "O motivo da denúncia é obrigatório")
        @Schema(description = "Categoria do motivo da denúncia")
        ReportReason reason,

        @NotBlank(message = "A descrição detalhada da denúncia é obrigatória")
        @Size(min = 10, max = 2000, message = "A descrição deve ter entre 10 e 2000 caracteres")
        @Schema(description = "Descrição detalhada explicando a violação ou problema encontrado")
        String description,

        @Schema(description = "URLs opcionais de evidências (máximo 5 links)")
        List<@Size(max = 1000, message = "Cada URL de evidência deve ter no máximo 1000 caracteres") String> evidenceUrls
) {
}
