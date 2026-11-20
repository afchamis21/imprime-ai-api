package org.imprime.ai.api.http.response.model3d;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.DimensionsDto;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhes completos de um modelo 3D")
public record Model3DDetailResponse(
        @Schema(description = "GUID único do modelo 3D", example = "7f9c24ef-1000-4000-8000-000000000001")
        String guid,
        @Schema(description = "Slug amigável do modelo", example = "dragon-articulated")
        String slug,
        @Schema(description = "Nome do modelo 3D", example = "Dragon Articulated")
        String name,
        @Schema(description = "Preço em BRL (0.00 quando gratuito)", example = "0.00")
        BigDecimal price,
        @Schema(description = "Descrição completa do modelo")
        String description,
        @Schema(description = "Categoria do modelo", example = "miniatures")
        String category,
        @Schema(description = "Nota média (0.00 a 5.00)", example = "4.90")
        BigDecimal rating,
        @Schema(description = "Total de avaliações", example = "1423")
        int reviewsCount,
        @Schema(description = "Total de downloads", example = "28400")
        long downloadsCount,
        @Schema(description = "Total de visualizações", example = "92000")
        long viewsCount,
        @Schema(description = "Peso estimado em gramas", example = "115")
        Integer estimatedWeightGrams,
        @Schema(description = "Dimensões da peça em milímetros")
        DimensionsDto dimensions,
        @Schema(description = "Material recomendado para impressão", example = "PLA")
        MaterialType recommendedMaterial,
        @Schema(description = "Formato do arquivo 3D", example = "GLB")
        FileFormat3D format,
        @Schema(description = "Tipo de licença de uso", example = "PERSONAL")
        LicenseType license,
        @Schema(description = "Indica se suporta customização paramétrica OpenSCAD", example = "true")
        boolean customizable,
        @Schema(description = "URL do arquivo 3D para visualização no ThreeViewer")
        String modelFileUrl,
        @Schema(description = "Tags do modelo")
        List<String> tags,
        @Schema(description = "Indica se foi favoritado pelo usuário autenticado", example = "false")
        boolean favorited,
        @Schema(description = "Dados do autor do modelo")
        ModelAuthorResponse author,
        @Schema(description = "URLs das imagens de apresentação")
        List<String> imageUrls,
        @Schema(description = "Data de publicação em UTC (ISO-8601)")
        Instant createdAt
) {
}
