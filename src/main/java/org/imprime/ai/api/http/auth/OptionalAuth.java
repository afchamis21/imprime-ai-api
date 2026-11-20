package org.imprime.ai.api.http.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Permite acesso anônimo ou autenticado via JWT.
 * Se o header Authorization estiver presente, o token será validado e o usuário populado no ServiceContext.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalAuth {
}
