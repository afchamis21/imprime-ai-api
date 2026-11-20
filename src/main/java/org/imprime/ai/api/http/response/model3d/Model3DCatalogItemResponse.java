package org.imprime.ai.api.http.response.model3d;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Item resumido de modelo 3D no catálogo público")
public record Model3DCatalogItemResponse(
        @Schema(description = "GUID único do modelo 3D", example = "7f9c24ef-1000-4000-8000-000000000001")
        String guid,
        @Schema(description = "Slug amigável do modelo", example = "dragon-articulated")
        String slug,
        @Schema(description = "Nome do modelo 3D", example = "Dragon Articulated")
        String name,
        @Schema(description = "Categoria do modelo", example = "miniatures")
        String category,
        @Schema(description = "Preço em BRL (0.00 para modelos gratuitos)", example = "0.00")
        BigDecimal price,
        @Schema(description = "Nota média (0.00 a 5.00)", example = "4.90")
        BigDecimal rating,
        @Schema(description = "Total de avaliações", example = "1423")
        int reviewsCount,
        @Schema(description = "Total de downloads realizados", example = "28400")
        long downloadsCount,
        @Schema(description = "Total de visualizações", example = "92000")
        long viewsCount,
        @Schema(description = "Formato principal do arquivo", example = "THREE_MF")
        FileFormat3D format,
        @Schema(description = "Tipo de licença de uso", example = "PERSONAL")
        LicenseType license,
        @Schema(description = "Indica se possui customização paramétrica OpenSCAD", example = "false")
        boolean customizable,
        @Schema(description = "Tags técnicas do modelo", example = "[\"PLA\", \"Sem Suporte\"]")
        List<String> tags,
        @Schema(description = "URL da imagem de capa")
        String coverImageUrl,
        @Schema(description = "Indica se foi favoritado pelo usuário autenticado", example = "false")
        boolean favorited,
        @Schema(description = "Autor do modelo")
        ModelAuthorResponse author,
        @Schema(description = "Data de publicação em UTC (ISO-8601)")
        Instant createdAt
) {
}
