package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Dados para adicionar um insumo extra à Lista de Materiais (BOM) do projeto")
public record CreateBomItemRequest(
        @Schema(description = "Descrição do insumo", example = "Ímãs Neodímio 6x3mm (pacote 20un)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do item da BOM é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @Schema(description = "Quantidade necessária", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser positiva")
        BigDecimal quantity,

        @Schema(description = "Unidade de medida (ex.: un, pct, m)", example = "pct")
        @Size(max = 30, message = "A unidade deve ter no máximo 30 caracteres")
        String unit,

        @Schema(description = "Custo unitário em BRL", example = "14.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O custo unitário é obrigatório")
        @PositiveOrZero(message = "O custo unitário não pode ser negativo")
        BigDecimal unitCost,

        @Schema(description = "Indica se o insumo já foi adquirido", example = "false")
        Boolean acquired
) {
}
