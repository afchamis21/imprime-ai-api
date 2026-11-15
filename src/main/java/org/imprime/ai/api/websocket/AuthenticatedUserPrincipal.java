package org.imprime.ai.api.websocket;

import org.imprime.ai.api.model.User;

import java.security.Principal;

public record AuthenticatedUserPrincipal(User user) implements Principal {

    @Override
    public String getName() {
        return user.getGuid();
    }
}