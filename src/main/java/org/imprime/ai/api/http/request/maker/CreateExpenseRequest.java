package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Dados para lançamento de despesa operacional do estúdio")
public record CreateExpenseRequest(
        @Schema(description = "Descrição da despesa", example = "Compra 2x Rolos PETG Preto", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A descrição da despesa é obrigatória")
        @Size(max = 180, message = "A descrição deve ter no máximo 180 caracteres")
        String title,

        @Schema(description = "Categoria da despesa operacional", example = "FILAMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A categoria da despesa é obrigatória")
        ExpenseCategory category,

        @Schema(description = "Valor da despesa em BRL", example = "240.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor é obrigatório")
        @Positive(message = "O valor deve ser maior que zero")
        BigDecimal amount,

        @Schema(description = "Data de competência do lançamento (YYYY-MM-DD)", example = "2026-05-19", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A data do lançamento é obrigatória")
        LocalDate date,

        @Schema(description = "Nota fiscal ou referência externa (opcional)", example = "NF-90112")
        @Size(max = 100, message = "A referência deve ter no máximo 100 caracteres")
        String reference
) {
}
