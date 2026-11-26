package org.imprime.ai.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "stripe")
public class StripeConfigProperties {
    private String publishableKey;
    private String secretKey;
    private String webhookSecret;
    private boolean allowSimulatedOrderCheckout;
    private String currency = "brl";
    private String successUrl = "https://imprimeai.shop/cart?payment=success&session_id={CHECKOUT_SESSION_ID}";
    private String cancelUrl = "https://imprimeai.shop/cart?payment=cancel";
}
