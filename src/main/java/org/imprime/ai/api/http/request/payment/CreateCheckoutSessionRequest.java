package org.imprime.ai.api.http.request.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.dto.AddressDTO;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados completos do pedido e carrinho para criação da sessão de pagamento Stripe Checkout")
public record CreateCheckoutSessionRequest(
        @Schema(description = "Lista de itens do carrinho")
        List<CheckoutItemRequest> items,
        @Schema(description = "GUID do endereço de entrega cadastrado pelo usuário")
        String addressGuid,
        @Schema(description = "Snapshot do endereço de entrega selecionado")
        AddressDTO shippingAddress,
        @Schema(description = "Nome completo do comprador")
        String customerName,
        @Schema(description = "E-mail do comprador para envio de recibo da Stripe")
        String customerEmail,
        @Schema(description = "Telefone de contato do comprador")
        String customerPhone,
        @Schema(description = "Observações adicionais para o pedido/entrega")
        String notes,
        @Schema(description = "Moeda da transação (padrão: brl)", example = "brl")
        String currency,
        @Schema(description = "Subtotal dos itens em reais", example = "119.80")
        BigDecimal subtotal,
        @Schema(description = "Valor do frete em reais", example = "18.90")
        BigDecimal shippingCost,
        @Schema(description = "Valor total do pedido (subtotal + frete)", example = "138.70")
        BigDecimal totalAmount,
        @Schema(description = "URL de redirecionamento em caso de pagamento aprovado")
        String successUrl,
        @Schema(description = "URL de redirecionamento caso o usuário cancele o checkout")
        String cancelUrl,
        @Schema(description = "GUIDs das cotações Melhor Envio selecionadas no checkout")
        List<String> shippingQuoteGuids
) {
    public CreateCheckoutSessionRequest(List<CheckoutItemRequest> items, String addressGuid, AddressDTO shippingAddress,
                                       String customerName, String customerEmail, String customerPhone, String notes,
                                       String currency, BigDecimal subtotal, BigDecimal shippingCost, BigDecimal totalAmount,
                                       String successUrl, String cancelUrl) {
        this(items, addressGuid, shippingAddress, customerName, customerEmail, customerPhone, notes, currency,
                subtotal, shippingCost, totalAmount, successUrl, cancelUrl, List.of());
    }
}
