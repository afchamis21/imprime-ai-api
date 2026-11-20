package org.imprime.ai.api.service;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.payment.CheckoutItemRequest;
import org.imprime.ai.api.http.request.payment.CreateCheckoutSessionRequest;
import org.imprime.ai.api.http.response.CheckoutSessionResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final StripeConfigProperties stripeConfig;
    private final AddressService addressService;

    public CheckoutSessionResponse createCheckoutSession(CreateCheckoutSessionRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException(MessageCd.INTERNAL_SERVER_ERROR);
        }

        User caller = ServiceContext.getUserOrThrow();

        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency().toLowerCase()
                : stripeConfig.getCurrency();

        BigDecimal calculatedSubtotal = request.items().stream()
                .map(item -> {
                    BigDecimal price = item.price() != null ? item.price() : BigDecimal.ZERO;
                    int qty = item.quantity() != null && item.quantity() > 0 ? item.quantity() : 1;
                    return price.multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingCost = request.shippingCost() != null ? request.shippingCost() : BigDecimal.ZERO;
        BigDecimal totalAmount = request.totalAmount() != null
                ? request.totalAmount()
                : calculatedSubtotal.add(shippingCost);

        String resolvedAddressSummary = "";
        if (request.addressGuid() != null && !request.addressGuid().isBlank()) {
            Optional<Address> optionalAddress = addressService.findByGuid(request.addressGuid());
            if (optionalAddress.isPresent()) {
                Address addr = optionalAddress.get();
                resolvedAddressSummary = String.format(
                        "%s - %s, %s/%s (%s)",
                        addr.getAddressLine1(),
                        addr.getNeighborhood(),
                        addr.getCity(),
                        addr.getState(),
                        addr.getZipCode()
                );
            }
        } else if (request.shippingAddress() != null) {
            var addr = request.shippingAddress();
            resolvedAddressSummary = String.format(
                    "%s - %s, %s/%s (%s)",
                    addr.addressLine1(),
                    addr.neighborhood(),
                    addr.city(),
                    addr.state(),
                    addr.zipCode()
            );
        }

        // Caso a chave secreta da Stripe ainda não esteja configurada no ambiente,
        // retorna sessão em modo SIMULATED para o frontend exibir o checkout simulado/fallback.
        if (stripeConfig.getSecretKey() == null || stripeConfig.getSecretKey().isBlank()) {
            log.warn("STRIPE_SECRET_KEY não configurada. Retornando sessão em modo SIMULATED para o usuário [{}].", caller.getGuid());
            return new CheckoutSessionResponse(
                    "cs_test_sim_" + UUID.randomUUID().toString().replace("-", ""),
                    null,
                    "SIMULATED",
                    totalAmount,
                    currency
            );
        }

        try {
            Stripe.apiKey = stripeConfig.getSecretKey();

            String successUrl = (request.successUrl() != null && !request.successUrl().isBlank())
                    ? request.successUrl()
                    : stripeConfig.getSuccessUrl();

            String cancelUrl = (request.cancelUrl() != null && !request.cancelUrl().isBlank())
                    ? request.cancelUrl()
                    : stripeConfig.getCancelUrl();

            String customerEmail = (request.customerEmail() != null && !request.customerEmail().isBlank())
                    ? request.customerEmail()
                    : caller.getEmail();

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setCustomerEmail(customerEmail)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .putMetadata("userGuid", caller.getGuid())
                    .putMetadata("addressGuid", request.addressGuid() != null ? request.addressGuid() : "")
                    .putMetadata("shippingAddress", resolvedAddressSummary)
                    .putMetadata("customerName", request.customerName() != null ? request.customerName() : (caller.getFirstName() + " " + caller.getLastName()).trim())
                    .putMetadata("customerPhone", request.customerPhone() != null ? request.customerPhone() : (caller.getPhoneNumber() != null ? caller.getPhoneNumber() : ""))
                    .putMetadata("notes", request.notes() != null ? request.notes() : "")
                    .putMetadata("totalAmount", totalAmount.toPlainString());

            for (CheckoutItemRequest item : request.items()) {
                BigDecimal unitPrice = item.price() != null ? item.price() : BigDecimal.ZERO;
                long unitAmountInCents = unitPrice
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();

                long quantity = item.quantity() != null && item.quantity() > 0 ? item.quantity() : 1L;

                String description = String.format(
                        "Maker: %s • Material: %s",
                        item.maker() != null ? item.maker() : "Imprime.AI",
                        item.material() != null ? item.material() : "Padrão"
                );

                SessionCreateParams.LineItem.PriceData.ProductData.Builder productBuilder =
                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(item.name() != null ? item.name() : "Item de Impressão 3D")
                                .setDescription(description);

                if (item.image() != null && item.image().startsWith("http")) {
                    productBuilder.addImage(item.image());
                }

                SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                        .setQuantity(quantity)
                        .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency(currency)
                                        .setUnitAmount(unitAmountInCents)
                                        .setProductData(productBuilder.build())
                                        .build()
                        )
                        .build();

                paramsBuilder.addLineItem(lineItem);
            }

            if (shippingCost.compareTo(BigDecimal.ZERO) > 0) {
                long shippingInCents = shippingCost
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();

                SessionCreateParams.LineItem shippingLineItem = SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency(currency)
                                        .setUnitAmount(shippingInCents)
                                        .setProductData(
                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                        .setName("Frete e Entrega")
                                                        .setDescription(resolvedAddressSummary.isBlank() ? "Entrega Padrão" : resolvedAddressSummary)
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();

                paramsBuilder.addLineItem(shippingLineItem);
            }

            Session session = Session.create(paramsBuilder.build());
            return new CheckoutSessionResponse(
                    session.getId(),
                    session.getUrl(),
                    "CREATED",
                    totalAmount,
                    currency
            );
        } catch (Exception e) {
            log.error("Erro ao criar sessão de pagamento na Stripe para o usuário [{}]", caller.getGuid(), e);
            throw new InternalErrorException();
        }
    }
}
