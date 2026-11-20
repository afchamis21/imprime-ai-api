package org.imprime.ai.api.websocket;

import lombok.Data;
import org.imprime.ai.api.config.OriginNormalizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "websocket")
public class WebSocketConfigProperties {
    private List<String> allowedOrigins = new ArrayList<>();

    public String[] resolveAllowedOrigins() {
        return OriginNormalizer.normalizeOrigins(allowedOrigins);
    }
}
