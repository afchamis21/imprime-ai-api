package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.http.response.model3d.ScadParameterDefinition;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Metadados da solicitação de envio de modelo 3D pelo perfil do Maker")
public record CreateMakerModelRequest(
        @Schema(description = "Nome do modelo 3D", example = "Suporte Modular Headset", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do modelo é obrigatório")
        @Size(max = 150, message = "O nome do modelo deve ter no máximo 150 caracteres")
        String name,

        @Schema(description = "Categoria do modelo 3D", example = "engineering", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A categoria do modelo é obrigatória")
        @Size(max = 80, message = "A categoria deve ter no máximo 80 caracteres")
        String category,

        @Schema(description = "Preço em BRL (0.00 para gratuito)", example = "0.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
        BigDecimal price,

        @Schema(description = "Descrição técnica e orientações de impressão")
        @Size(max = 4000, message = "A descrição deve ter no máximo 4000 caracteres")
        String description,

        @Schema(description = "Formato principal do arquivo (STL, THREE_MF, OBJ, GLB, SCAD). Inferido automaticamente da extensão quando omitido.")
        FileFormat3D format,

        @Schema(description = "Licença de uso do arquivo", example = "PERSONAL")
        LicenseType license,

        @Schema(description = "Material recomendado para impressão", example = "PETG")
        MaterialType recommendedMaterial,

        @Schema(description = "Peso estimado da peça em gramas", example = "85")
        @Positive(message = "O peso estimado deve ser maior que zero")
        Integer estimatedWeightGrams,

        @Schema(description = "Largura em milímetros", example = "120.00")
        @Positive(message = "A largura deve ser maior que zero")
        BigDecimal widthMm,

        @Schema(description = "Profundidade em milímetros", example = "95.00")
        @Positive(message = "A profundidade deve ser maior que zero")
        BigDecimal depthMm,

        @Schema(description = "Altura em milímetros", example = "140.00")
        @Positive(message = "A altura deve ser maior que zero")
        BigDecimal heightMm,

        @Schema(description = "Indica se o modelo possui parametrização customizável", example = "false")
        Boolean customizable,

        @Schema(description = "URL da imagem principal de capa")
        @Size(max = 1000)
        String coverImageUrl,

        @Schema(description = "URLs das imagens de preview. Envie ao menos uma imagem na lista ou na capa.")
        List<String> imageUrls,

        @Schema(description = "Tags de busca e recomendação técnica")
        List<String> tags,

        @Schema(description = "Código-fonte OpenSCAD (obrigatório quando customizable = true e nenhum arquivo .scad for enviado)")
        @Size(max = 200000, message = "O código OpenSCAD deve ter no máximo 200000 caracteres")
        String scadCode,

        @Schema(description = "Lista opcional de definições de parâmetros OpenSCAD; quando omitida, é extraída automaticamente do scadCode")
        List<ScadParameterDefinition> parameters
) {
    public CreateMakerModelRequest(
            String name,
            String category,
            BigDecimal price,
            String description,
            FileFormat3D format,
            LicenseType license,
            MaterialType recommendedMaterial,
            Integer estimatedWeightGrams,
            BigDecimal widthMm,
            BigDecimal depthMm,
            BigDecimal heightMm,
            Boolean customizable,
            String coverImageUrl,
            List<String> imageUrls,
            List<String> tags
    ) {
        this(
                name,
                category,
                price,
                description,
                format,
                license,
                recommendedMaterial,
                estimatedWeightGrams,
                widthMm,
                depthMm,
                heightMm,
                customizable,
                coverImageUrl,
                imageUrls,
                tags,
                null,
                null
        );
    }
}
