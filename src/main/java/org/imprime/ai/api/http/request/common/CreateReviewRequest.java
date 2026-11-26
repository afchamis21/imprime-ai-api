package org.imprime.ai.api.http.request.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Dados para publicação de uma avaliação (o autor é extraído automaticamente do token JWT)")
public record CreateReviewRequest(
        @Schema(description = "Nota atribuída de 1 a 5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A nota é obrigatória")
        @Min(value = 1, message = "A nota mínima é 1")
        @Max(value = 5, message = "A nota máxima é 5")
        Integer rating,

        @Schema(description = "Texto da avaliação", example = "Encaixe perfeito e ótimo acabamento em PETG!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O comentário é obrigatório")
        @Size(max = 2000, message = "O comentário deve ter no máximo 2000 caracteres")
        String comment,
        @Size(max = 4)
        List<@NotBlank @Size(max = 36) String> photoGuids
) {
    public CreateReviewRequest(Integer rating, String comment) {
        this(rating, comment, null);
    }
}
