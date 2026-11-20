package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.ProjectStatus;

import java.time.LocalDate;

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
        String notes
) {
}
