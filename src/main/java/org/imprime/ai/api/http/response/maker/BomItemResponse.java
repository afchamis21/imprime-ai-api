package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Item de insumo extra (BOM) pertencente a um projeto multi-peça")
public record BomItemResponse(
        @Schema(description = "GUID único do item da BOM")
        String guid,
        @Schema(description = "Nome/descrição do insumo", example = "Ímãs Neodímio 6x3mm (pacote 20un)")
        String name,
        @Schema(description = "Quantidade necessária", example = "1")
        BigDecimal quantity,
        @Schema(description = "Unidade de medida", example = "pct")
        String unit,
        @Schema(description = "Custo unitário em BRL", example = "14.00")
        BigDecimal unitCost,
        @Schema(description = "Indica se o insumo já foi adquirido", example = "true")
        boolean acquired
) {
}
