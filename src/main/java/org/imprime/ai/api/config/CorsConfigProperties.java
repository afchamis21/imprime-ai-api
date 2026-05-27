package org.imprime.ai.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsConfigProperties {
    private String[] allowedOrigins;
    private String[] allowedMethods;
    private String pathPattern;
}
