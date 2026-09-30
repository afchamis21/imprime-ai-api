package org.imprime.ai.api.http;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.imprime.ai.api.http.response.ResponseMessage;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.LanguageCd;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.service.MessageLkupService;
import org.slf4j.MDC;

import java.util.*;

@Data
public class ServiceContext {

    private static final String TRANSACTION_ID_MDC_KEY = "transaction-id";
    private static final String USER_ID_MDC_KEY = "user-id";

    private static final ThreadLocal<ServiceContext> contextHolder =
            new ThreadLocal<>();

    private MessageLkupService messageLkupService;

    private @Nullable User user;

    private @Nullable LanguageCd languageCd;

    private List<Exception> exceptions = new ArrayList<>();

    private List<ResponseMessage> messages = new ArrayList<>();

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

    public static ServiceContext copy(ServiceContext ctx) {
        ServiceContext copy = new ServiceContext();
        copy.setUser(ctx.user);
        copy.setLanguageCd(ctx.languageCd);
        copy.setExceptions(ctx.exceptions);

        contextHolder.set(copy);
        return copy;
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

    public static void addException(Exception exception) {
        getContext().exceptions.add(exception);
    }

    public static Optional<User> getUser() {
        return  Optional.ofNullable(getContext().user);
    }

    public static User getUserOrThrow() {
        return getUser().orElseThrow(UnauthorizedException::new);
    }

    public void addMessage(MessageCd messageCd, String ...args) {
        if (messageLkupService == null) return;

        messageLkupService.getMessageByCode(messageCd, args);
    }

    public List<ResponseMessage> getMessages() {
        return Objects.requireNonNullElse(messages, List.of());
    }
}