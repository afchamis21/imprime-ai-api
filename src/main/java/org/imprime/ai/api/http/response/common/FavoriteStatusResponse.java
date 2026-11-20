package org.imprime.ai.api.http.response.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.FavoriteTargetType;

@Schema(description = "Estado atual de favorito de um recurso para o usuário autenticado")
public record FavoriteStatusResponse(
        @Schema(description = "GUID do recurso favoritado/desfavoritado", example = "550e8400-e29b-41d4-a716-446655440000")
        String targetGuid,
        @Schema(description = "Tipo do recurso favoritado", example = "PRODUCT")
        FavoriteTargetType targetType,
        @Schema(description = "Indica se o recurso está marcado como favorito", example = "true")
        boolean favorited
) {
}
