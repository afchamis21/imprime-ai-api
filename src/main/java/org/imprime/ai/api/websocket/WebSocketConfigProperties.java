package org.imprime.ai.api.websocket;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "websocket")
public class WebSocketConfigProperties {
    private String[] allowedOrigins;
}
