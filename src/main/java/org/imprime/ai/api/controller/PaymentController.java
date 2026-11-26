package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.service.QuoteCheckoutService;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.payment.CompleteCheckoutRequest;
import org.imprime.ai.api.http.request.payment.CreateCheckoutSessionRequest;
import org.imprime.ai.api.http.request.payment.CalculateShippingQuotesRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.CheckoutSessionResponse;
import org.imprime.ai.api.http.response.CompleteCheckoutResponse;
import org.imprime.ai.api.http.response.payment.ShippingQuotesResponse;
import org.imprime.ai.api.service.PaymentService;
import org.imprime.ai.api.service.ShippingQuoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
@Tag(name = "Pagamentos (Stripe)", description = "Endpoints para criação de sessão de checkout na Stripe e recebimento de webhooks")
public class PaymentController {
    private final PaymentService paymentService;
    private final ShippingQuoteService shippingQuoteService;
    private final QuoteCheckoutService quoteCheckout;
    private final StripeConfigProperties stripe;

    @JwtAuthenticated
    @PostMapping("/quote-orders/{orderGuid}/checkout")
    public ResponseEntity<BaseResponse<CheckoutSessionResponse>> createQuoteCheckout(@PathVariable String orderGuid) {
        return BaseResponse.ok(quoteCheckout.create(orderGuid));
    }

    @JwtAuthenticated
    @PostMapping("/quote-orders/{orderGuid}/confirm")
    public ResponseEntity<Void> confirmQuoteCheckout(@PathVariable String orderGuid,
                                                     @Valid @RequestBody ConfirmQuoteCheckout request) {
        quoteCheckout.confirm(orderGuid, request.sessionId());
        return ResponseEntity.noContent().build();
    }

    public record ConfirmQuoteCheckout(@NotBlank @Size(max = 255) String sessionId) {}

    @JwtAuthenticated
    @PostMapping("/shipping/quotes")
    @Operation(summary = "Calcular opções de frete por Maker", description = "Consulta o Melhor Envio no backend e retorna serviços disponíveis para cada Maker físico do carrinho.")
    public ResponseEntity<BaseResponse<ShippingQuotesResponse>> calculateShippingQuotes(
            @RequestBody CalculateShippingQuotesRequest request) {
        return BaseResponse.ok(shippingQuoteService.calculate(request));
    }

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
            summary = "Concluir checkout do carrinho e gerar pedidos",
            description = "Confirma o pagamento do carrinho e persiste pedidos, itens, modelos adquiridos e transições iniciais de status."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Checkout finalizado e pedidos criados/recuperados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, endereço ausente ou sessão não paga", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Endereço não pertence ao usuário autenticado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @JwtAuthenticated
    @PostMapping("/checkout/complete")
    public ResponseEntity<BaseResponse<CompleteCheckoutResponse>> completeCheckout(
            @RequestBody CompleteCheckoutRequest request
    ) {
        return BaseResponse.ok(paymentService.completeCartCheckout(request));
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
        if (!StringUtils.hasText(stripe.getWebhookSecret())) return ResponseEntity.status(503).build();
        if (!StringUtils.hasText(sigHeader)) return ResponseEntity.badRequest().build();
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripe.getWebhookSecret());
        } catch (SignatureVerificationException | RuntimeException ex) {
            return ResponseEntity.badRequest().build();
        }
        if ("checkout.session.completed".equals(event.getType())
                || "checkout.session.async_payment_succeeded".equals(event.getType())) {
            if (!(event.getDataObjectDeserializer().getObject().orElse(null) instanceof Session session)) {
                return ResponseEntity.status(503).build();
            }
            quoteCheckout.handleCompletedSession(session);
        }
        return ResponseEntity.ok("received");
    }
}
