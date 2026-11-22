package org.imprime.ai.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "melhor-envio")
public class MelhorEnvioProperties {
    private String baseUrl = "https://sandbox.melhorenvio.com.br";
    private String accessToken;
    private String refreshToken;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String userAgent;
}
