package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Dados para atualização parcial (PATCH) de um item da Lista de Materiais (BOM)")
public record UpdateBomItemRequest(
        @Schema(description = "Descrição atualizada do insumo")
        @Size(min = 1, max = 150, message = "O nome deve ter entre 1 e 150 caracteres")
        String name,

        @Schema(description = "Quantidade necessária atualizada")
        @Positive(message = "A quantidade deve ser positiva")
        BigDecimal quantity,

        @Schema(description = "Unidade de medida")
        @Size(max = 30, message = "A unidade deve ter no máximo 30 caracteres")
        String unit,

        @Schema(description = "Custo unitário atualizado em BRL")
        @PositiveOrZero(message = "O custo unitário não pode ser negativo")
        BigDecimal unitCost,

        @Schema(description = "Marca se o insumo já foi adquirido")
        Boolean acquired
) {
}
