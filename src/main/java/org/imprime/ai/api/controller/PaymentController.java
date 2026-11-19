package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.payment.CreateCheckoutSessionRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.CheckoutSessionResponse;
import org.imprime.ai.api.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
@Tag(name = "Pagamentos (Stripe)", description = "Endpoints para criação de sessão de checkout na Stripe e recebimento de webhooks")
public class PaymentController {
    private final PaymentService paymentService;

    @Operation(
            summary = "Criar sessão de pagamento no Stripe Checkout",
            description = "Recebe os itens do carrinho, endereço de entrega selecionado e dados do comprador, cria uma sessão de pagamento na Stripe e retorna a URL de redirecionamento (`checkoutUrl`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sessão de checkout criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do carrinho inválidos ou vazios", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @JwtAuthenticated
    @PostMapping("/checkout")
    public ResponseEntity<BaseResponse<CheckoutSessionResponse>> createCheckoutSession(
            @RequestBody CreateCheckoutSessionRequest request
    ) {
        return BaseResponse.ok(paymentService.createCheckoutSession(request));
    }

    @Operation(
            summary = "Webhook de eventos da Stripe",
            description = "Endpoint público chamado pela Stripe para notificar confirmações de pagamento (`checkout.session.completed`)."
    )
    @SkipAuth
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
    ) {
        log.info("Recebido evento de webhook da Stripe (assinatura presente: {})", sigHeader != null);
        return ResponseEntity.ok("received");
    }
}
