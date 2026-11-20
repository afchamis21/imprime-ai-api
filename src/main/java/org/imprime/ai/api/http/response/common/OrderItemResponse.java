package org.imprime.ai.api.http.response.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Schema(description = "Item pertencente a um pedido")
public record OrderItemResponse(
        @Schema(description = "GUID do produto (nulo em pedidos sob demanda)", example = "a1b2c3d4-0000-4000-8000-000000000101")
        String productGuid,
        @Schema(description = "Nome do item ou peça", example = "Suporte Universal para Controle")
        String name,
        @Schema(description = "Material utilizado", example = "PETG")
        MaterialType material,
        @Schema(description = "Nome da cor selecionada", example = "Preto")
        String colorName,
        @Schema(description = "Quantidade de unidades", example = "2")
        int quantity,
        @Schema(description = "Preço unitário em BRL", example = "45.90")
        BigDecimal unitPrice,
        @Schema(description = "Total da linha (unitPrice * quantity)", example = "91.80")
        BigDecimal lineTotal
) {
}
