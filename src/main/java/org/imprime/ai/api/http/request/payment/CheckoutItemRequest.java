package org.imprime.ai.api.http.request.payment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Item individual do carrinho enviado para o checkout na Stripe")
public record CheckoutItemRequest(
        @Schema(description = "ID ou GUID do produto, modelo ou pedido", example = "1")
        String id,
        @Schema(description = "Nome do produto ou modelo 3D", example = "Suporte Ergonômico Monitor")
        String name,
        @Schema(description = "Nome do Maker responsável ou designer", example = "Studio Impressão 3D")
        String maker,
        @Schema(description = "Material selecionado para impressão", example = "PLA+")
        String material,
        @Schema(description = "URL da imagem do produto")
        String image,
        @Schema(description = "Preço unitário em reais (BRL)", example = "59.90")
        BigDecimal price,
        @Schema(description = "Quantidade do item", example = "2")
        Integer quantity
) {
}
