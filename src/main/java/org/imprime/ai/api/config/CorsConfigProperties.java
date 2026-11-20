package org.imprime.ai.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsConfigProperties {
    private List<String> allowedOrigins = new ArrayList<>();
    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
    private List<String> exposedHeaders = new ArrayList<>(List.of("X-Transaction-Id", "Content-Disposition"));
    private String pathPattern = "/**";
    private long maxAge = 3600L;

    public String[] resolveAllowedOrigins() {
        return OriginNormalizer.normalizeOrigins(allowedOrigins);
    }

    public String[] resolveAllowedMethods() {
        return OriginNormalizer.normalizeValues(allowedMethods);
    }

    public String[] resolveExposedHeaders() {
        return OriginNormalizer.normalizeValues(exposedHeaders);
    }
}
