package org.imprime.ai.api.http.request.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.dto.AddressDTO;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Payload enviado após confirmação do pagamento do carrinho para materializar pedidos, itens e modelos adquiridos")
public record CompleteCheckoutRequest(
        @Schema(description = "ID da sessão de checkout na Stripe (ou simulada)", example = "cs_test_a1b2c3d4")
        String sessionId,
        @Schema(description = "Lista de itens adquiridos no carrinho")
        List<CheckoutItemRequest> items,
        @Schema(description = "GUID do endereço de entrega selecionado pelo comprador")
        String addressGuid,
        @Schema(description = "Snapshot do endereço de entrega caso ainda não esteja vinculado por GUID")
        AddressDTO shippingAddress,
        @Schema(description = "Nome completo do comprador")
        String customerName,
        @Schema(description = "E-mail do comprador")
        String customerEmail,
        @Schema(description = "Telefone de contato do comprador")
        String customerPhone,
        @Schema(description = "Observações adicionais do pedido")
        String notes,
        @Schema(description = "Moeda da transação", example = "brl")
        String currency,
        @Schema(description = "Subtotal dos itens em BRL", example = "119.80")
        BigDecimal subtotal,
        @Schema(description = "Valor total do frete em BRL", example = "18.90")
        BigDecimal shippingCost,
        @Schema(description = "Valor total pago em BRL", example = "138.70")
        BigDecimal totalAmount,
        @Schema(description = "GUIDs das cotações Melhor Envio selecionadas no checkout")
        List<String> shippingQuoteGuids
) {
    public CompleteCheckoutRequest(String sessionId, List<CheckoutItemRequest> items, String addressGuid,
                                   AddressDTO shippingAddress, String customerName, String customerEmail,
                                   String customerPhone, String notes, String currency, BigDecimal subtotal,
                                   BigDecimal shippingCost, BigDecimal totalAmount) {
        this(sessionId, items, addressGuid, shippingAddress, customerName, customerEmail, customerPhone, notes,
                currency, subtotal, shippingCost, totalAmount, List.of());
    }
}
