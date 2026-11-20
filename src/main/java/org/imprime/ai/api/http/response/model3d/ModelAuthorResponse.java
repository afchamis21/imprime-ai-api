package org.imprime.ai.api.http.response.model3d;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados públicos resumidos do autor de um modelo 3D")
public record ModelAuthorResponse(
        @Schema(description = "GUID do autor/usuário", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        String guid,
        @Schema(description = "Slug do perfil público do autor", example = "hiroshi-tanaka")
        String slug,
        @Schema(description = "Nome de exibição do autor", example = "Hiroshi T.")
        String name,
        @Schema(description = "URL do avatar do autor")
        String avatarUrl,
        @Schema(description = "Indica se o autor é verificado", example = "true")
        boolean verified
) {
}
