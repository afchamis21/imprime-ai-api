package org.imprime.ai.api.http.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.AppConfig;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.enums.LanguageCd;
import org.imprime.ai.api.service.MessageLkupService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceContextInterceptor implements HandlerInterceptor {
    private static final String TRANSACTION_ID_HEADER = "X-Transaction-Id";
    private static final String LANGUAGE_CD_HEADER = "X-Language-Code";

    private final MessageLkupService messageLkupService;
    private final AppConfig appConfig;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        ServiceContext ctx = ServiceContext.getContext();
        ctx.setMessageLkupService(messageLkupService);
        response.setHeader(TRANSACTION_ID_HEADER, ctx.getTransactionId());

        String languageCode = request.getHeader(LANGUAGE_CD_HEADER);
        LanguageCd lang = LanguageCd.fromCode(languageCode);
        if (lang == null) {
            lang = appConfig.getDefaultLanguage();
        }

        ctx.setLanguageCd(lang);
        ctx.setIpAddress(resolveClientIp(request));
        ctx.setUserAgent(resolveUserAgent(request));

        log.info("Starting execution of [{} {}]", request.getMethod(), request.getRequestURI());

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first.length() > 64 ? first.substring(0, 64) : first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null) return null;
        return remoteAddr.length() > 64 ? remoteAddr.substring(0, 64) : remoteAddr;
    }

    private String resolveUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return null;
        }
        String trimmed = ua.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex) throws Exception {
        List<Exception> exceptionList = ServiceContext.getContext().getExceptions();
        log.info("[{}] exceptions encountered during execution of [{} {}]", exceptionList.size(), request.getMethod(), request.getRequestURI());
        for (Exception exception : exceptionList) {
            log.error(exception.getMessage(), exception);
        }

        log.info("Finished execution of [{} {}]", request.getMethod(), request.getRequestURI());

        ServiceContext.clear();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
