package org.imprime.ai.api.http.request.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para avaliação de um Maker vinculada a um pedido concluído")
public record CreateMakerReviewRequest(
        @Schema(description = "Nota atribuída de 1 a 5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A nota é obrigatória")
        @Min(value = 1, message = "A nota mínima é 1")
        @Max(value = 5, message = "A nota máxima é 5")
        Integer rating,

        @Schema(description = "Texto da avaliação", example = "Atendimento excelente e entrega antes do prazo!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O comentário é obrigatório")
        @Size(max = 2000, message = "O comentário deve ter no máximo 2000 caracteres")
        String comment,

        @Schema(description = "GUID do pedido concluído com o Maker (opcional; usa automaticamente o pedido elegível mais recente se omitido)")
        String orderGuid
) {
}
