package org.imprime.ai.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {
    private final CorsConfigProperties corsConfigProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(corsConfigProperties.getPathPattern())
                .allowedOrigins(corsConfigProperties.getAllowedOrigins())
                .allowedMethods(corsConfigProperties.getAllowedMethods())
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
