package org.imprime.ai.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {
    private final CorsConfigProperties corsConfigProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String rawPattern = corsConfigProperties.getPathPattern();
        String pathPattern = (rawPattern == null || rawPattern.isBlank()) ? "/**" : rawPattern.trim();

        String[] allowedOrigins = corsConfigProperties.resolveAllowedOrigins();
        String[] allowedMethods = corsConfigProperties.resolveAllowedMethods();
        String[] exposedHeaders = corsConfigProperties.resolveExposedHeaders();

        CorsRegistration registration = registry.addMapping(pathPattern)
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods(allowedMethods)
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(corsConfigProperties.getMaxAge());

        if (exposedHeaders.length > 0) {
            registration.exposedHeaders(exposedHeaders);
        }
    }
}
