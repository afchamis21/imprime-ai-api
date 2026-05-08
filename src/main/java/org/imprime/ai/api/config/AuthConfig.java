package org.imprime.ai.api.config;

import lombok.Data;
import org.imprime.ai.api.model.AuthToken;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthConfig {
    private TokenConfig accessToken;
    private TokenConfig refreshToken;

    @Data
    public static class TokenConfig {
        private Duration duration;
        private String key;
        private AuthToken.TokenType tokenType;
    }
}
