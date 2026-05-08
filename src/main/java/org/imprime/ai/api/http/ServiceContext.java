package org.imprime.ai.api.http;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.imprime.ai.api.model.User;
import org.slf4j.MDC;

import java.util.UUID;

@Data
public class ServiceContext {

    private static final String TRANSACTION_ID_MDC_KEY = "transaction-id";
    private static final String USER_ID_MDC_KEY = "user-id";

    private static final ThreadLocal<ServiceContext> contextHolder =
            new ThreadLocal<>();

    @Nullable
    private User user;

    private final String transactionId = UUID.randomUUID().toString();

    private ServiceContext() {
        updateMdc();
    }

    public static ServiceContext getContext() {
        ServiceContext ctx = contextHolder.get();

        if (ctx == null) {
            ctx = new ServiceContext();
            contextHolder.set(ctx);
        }

        return ctx;
    }

    public static void clear() {
        MDC.remove(TRANSACTION_ID_MDC_KEY);
        MDC.remove(USER_ID_MDC_KEY);

        contextHolder.remove();
    }

    public void setUser(@Nullable User user) {
        this.user = user;
        updateMdc();
    }

    private void updateMdc() {
        MDC.put(TRANSACTION_ID_MDC_KEY, transactionId);

        if (user != null && user.getId() != null) {
            MDC.put(USER_ID_MDC_KEY, user.getId().toString());
        } else {
            MDC.remove(USER_ID_MDC_KEY);
        }
    }
}