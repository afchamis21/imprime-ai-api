package org.imprime.ai.api.config;

import org.imprime.ai.api.websocket.WebSocketConfig;
import org.imprime.ai.api.websocket.WebSocketConfigProperties;
import org.imprime.ai.api.websocket.interceptor.WebSocketAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorsAndWebSocketConfigTest {

    private static class InspectableCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return super.getCorsConfigurations();
        }
    }

    @Test
    void originNormalizerSplitsTrimsStripsTrailingSlashesAndDeduplicates() {
        List<String> raw = Arrays.asList(
                null,
                "   ",
                "https://imprimeai.shop/",
                " https://www.imprimeai.shop/// , https://api.imprimeai.shop ",
                "https://imprimeai.shop",
                "http://localhost:5173/"
        );

        String[] normalized = OriginNormalizer.normalizeOrigins(raw);

        assertArrayEquals(
                new String[]{
                        "https://imprimeai.shop",
                        "https://www.imprimeai.shop",
                        "https://api.imprimeai.shop",
                        "http://localhost:5173"
                },
                normalized
        );
    }

    @Test
    void corsConfigAllowsConfiguredDomainsAndRejectsUnknownOrigins() {
        CorsConfigProperties props = new CorsConfigProperties();
        props.setAllowedOrigins(List.of(
                "https://imprimeai.shop",
                "https://www.imprimeai.shop",
                "https://api.imprimeai.shop",
                "http://localhost:5173",
                "https://extra.imprimeai.shop/, https://imprimeai.shop"
        ));

        CorsConfig corsConfig = new CorsConfig(props);
        InspectableCorsRegistry registry = new InspectableCorsRegistry();
        corsConfig.addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/**");
        assertNotNull(configuration);

        assertEquals("https://imprimeai.shop", configuration.checkOrigin("https://imprimeai.shop"));
        assertEquals("https://www.imprimeai.shop", configuration.checkOrigin("https://www.imprimeai.shop"));
        assertEquals("https://api.imprimeai.shop", configuration.checkOrigin("https://api.imprimeai.shop"));
        assertEquals("http://localhost:5173", configuration.checkOrigin("http://localhost:5173"));
        assertEquals("https://extra.imprimeai.shop", configuration.checkOrigin("https://extra.imprimeai.shop"));
        assertNull(configuration.checkOrigin("https://evil.example.com"));

        assertTrue(configuration.getAllowedMethods().containsAll(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")));
        assertTrue(configuration.getExposedHeaders().containsAll(List.of("X-Transaction-Id", "Content-Disposition")));
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
        assertEquals(3600L, configuration.getMaxAge());
    }

    @Test
    void webSocketConfigRegistersNormalizedOriginPatterns() {
        WebSocketConfigProperties props = new WebSocketConfigProperties();
        props.setAllowedOrigins(List.of(
                "https://imprimeai.shop/",
                "https://www.imprimeai.shop, https://api.imprimeai.shop/",
                ""
        ));

        WebSocketAuthInterceptor interceptor = mock(WebSocketAuthInterceptor.class);
        WebSocketConfig webSocketConfig = new WebSocketConfig(props, interceptor);

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any())).thenReturn(registration);

        webSocketConfig.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(registration).setAllowedOriginPatterns(
                "https://imprimeai.shop",
                "https://www.imprimeai.shop",
                "https://api.imprimeai.shop"
        );
    }

    @Test
    void corsConfigAllowsAdminOriginsAndRejectsUnknownOrigins() {
        CorsConfigProperties props = new CorsConfigProperties();
        props.setAllowedOrigins(List.of(
                "http://localhost:5174",
                "http://127.0.0.1:5174",
                "http://localhost:4174",
                "https://admin.imprimeai.shop"
        ));

        CorsConfig corsConfig = new CorsConfig(props);
        InspectableCorsRegistry registry = new InspectableCorsRegistry();
        corsConfig.addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/**");
        assertNotNull(configuration);

        assertEquals("http://localhost:5174", configuration.checkOrigin("http://localhost:5174"));
        assertEquals("http://127.0.0.1:5174", configuration.checkOrigin("http://127.0.0.1:5174"));
        assertEquals("http://localhost:4174", configuration.checkOrigin("http://localhost:4174"));
        assertEquals("https://admin.imprimeai.shop", configuration.checkOrigin("https://admin.imprimeai.shop"));
        assertNull(configuration.checkOrigin("https://evil-admin.example.com"));
    }
}
