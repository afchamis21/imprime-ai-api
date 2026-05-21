package org.imprime.ai.api.http.config;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.interceptor.AuthInterceptor;
import org.imprime.ai.api.http.interceptor.ServiceContextInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@RequiredArgsConstructor
public class HttpConfig implements WebMvcConfigurer {
    private final ServiceContextInterceptor serviceContextInterceptor;
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(serviceContextInterceptor).order(1);
        registry.addInterceptor(authInterceptor).order(2);
    }
}
