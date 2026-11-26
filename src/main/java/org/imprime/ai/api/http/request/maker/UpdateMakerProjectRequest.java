package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Dados para atualização parcial (PATCH) de um projeto multi-peça (itens de BOM possuem sub-recurso próprio em `/bom`)")
public record UpdateMakerProjectRequest(
        @Schema(description = "Nome atualizado")
        @Size(min = 1, max = 150, message = "O nome deve ter entre 1 e 150 caracteres")
        String name,

        @Schema(description = "Descrição atualizada")
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        @Schema(description = "Status do projeto")
        ProjectStatus status,

        @Schema(description = "Data limite atualizada (YYYY-MM-DD)")
        LocalDate dueDate,

        @Schema(description = "Quantidade de mesas concluídas")
        @Min(value = 0, message = "O número de mesas concluídas não pode ser negativo")
        Integer completedPlates,

        @Schema(description = "Quantidade de peças concluídas")
        @Min(value = 0, message = "O número de peças concluídas não pode ser negativo")
        Integer completedParts,

        @Schema(description = "Notas internas atualizadas")
        @Size(max = 1000, message = "As notas devem ter no máximo 1000 caracteres")
        String notes,

        @Size(max = 120, message = "O nome do cliente deve ter no máximo 120 caracteres")
        String clientName,

        @Min(value = 1, message = "O projeto deve ter ao menos 1 mesa")
        Integer totalPlates,

        @Min(value = 1, message = "O projeto deve ter ao menos 1 peça")
        Integer totalParts,

        @PositiveOrZero(message = "O peso estimado não pode ser negativo")
        BigDecimal estimatedWeightGrams,

        @PositiveOrZero(message = "As horas estimadas não podem ser negativas")
        BigDecimal estimatedHours,

        @Positive(message = "O valor orçado deve ser positivo")
        BigDecimal quotedPrice,

        @PositiveOrZero(message = "O custo estimado não pode ser negativo")
        BigDecimal estimatedCost,

        MaterialType primaryMaterial,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor hexadecimal inválida")
        String colorHex,

        @Schema(description = "Filamentos e gramas para o projeto inteiro")
        @Size(max = 50)
        List<@NotNull @Valid ProjectFilamentUsageRequest> filamentUsage,

        @Schema(description = "Peças por mesa. Quando informado, o total de mesas é calculado.")
        @Min(value = 1, message = "A quantidade por mesa deve ser de pelo menos 1 peça")
        Integer partsPerPlate
) {
}
