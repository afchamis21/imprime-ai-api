package org.imprime.ai.api.http.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Dados da sessão de pagamento criada no Stripe Checkout")
public record CheckoutSessionResponse(
        @Schema(description = "ID da sessão na Stripe (ex.: cs_test_...)", example = "cs_test_a1b2c3d4")
        String sessionId,
        @Schema(description = "URL hospedada do Stripe Checkout para redirecionamento do cliente")
        String checkoutUrl,
        @Schema(description = "Status da criação da sessão (`CREATED` quando gerada na Stripe ou `SIMULATED` quando a chave secreta não está configurada)", example = "CREATED")
        String status,
        @Schema(description = "Valor total cobrado na sessão", example = "138.70")
        BigDecimal totalAmount,
        @Schema(description = "Moeda utilizada na sessão", example = "brl")
        String currency
) {
}
