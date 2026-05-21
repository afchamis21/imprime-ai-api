package org.imprime.ai.api.http.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
public class ServiceContextInterceptor implements HandlerInterceptor {
    private static final String TRANSACTION_ID_HEADER = "X-Transaction-Id";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        ServiceContext ctx = ServiceContext.getContext();
        response.setHeader(TRANSACTION_ID_HEADER, ctx.getTransactionId());

        log.info("Starting execution of [{} {}]", request.getMethod(), request.getRequestURI());

        return HandlerInterceptor.super.preHandle(request, response, handler);
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
