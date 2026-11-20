package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Metadados do arquivo 3D enviado ao Print Archive do Maker")
public record CreateArchiveFileRequest(
        @Schema(description = "Título legível para exibição no acervo", example = "Suporte Controle PS5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome de exibição é obrigatório")
        @Size(max = 150, message = "O nome de exibição deve ter no máximo 150 caracteres")
        String displayName,

        @Schema(description = "Formato do arquivo 3D", example = "THREE_MF", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O formato do arquivo é obrigatório")
        FileFormat3D format,

        @Schema(description = "Material previsto para impressão", example = "PLA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O material é obrigatório")
        MaterialType material,

        @Schema(description = "Nome da cor prevista", example = "Branco")
        @Size(max = 60, message = "O nome da cor deve ter no máximo 60 caracteres")
        String colorName,

        @Schema(description = "Código hexadecimal #RRGGBB da cor prevista", example = "#f1f5f9")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor hexadecimal inválida")
        String colorHex,

        @Schema(description = "GUID do rolo de filamento vinculado (opcional)")
        String filamentGuid,

        @Schema(description = "Peso estimado de material em gramas", example = "120.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O peso estimado é obrigatório")
        @Positive(message = "O peso estimado deve ser positivo")
        BigDecimal estimatedWeightGrams,

        @Schema(description = "Tempo estimado de impressão em minutos", example = "150", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O tempo estimado é obrigatório")
        @Min(value = 1, message = "O tempo estimado deve ser de pelo menos 1 minuto")
        Integer estimatedTimeMinutes,

        @Schema(description = "Altura de camada em mm", example = "0.20")
        @Positive(message = "A altura de camada deve ser positiva")
        BigDecimal layerHeightMm,

        @Schema(description = "Percentual de preenchimento (0 a 100)", example = "20")
        @Min(value = 0, message = "O preenchimento mínimo é 0%")
        @Max(value = 100, message = "O preenchimento máximo é 100%")
        Integer infillPercent,

        @Schema(description = "Quantidade de mesas/placas no arquivo", example = "1")
        @Min(value = 1, message = "Mínimo de 1 mesa")
        Integer platesCount,

        @Schema(description = "Tags organizacionais")
        @Size(max = 10, message = "Máximo de 10 tags")
        List<String> tags,

        @Schema(description = "Origem do arquivo (ex.: Upload Local, MakerWorld)", example = "Upload Local")
        @Size(max = 100, message = "A origem deve ter no máximo 100 caracteres")
        String sourceName,

        @Schema(description = "GUID do projeto multi-peça associado (opcional)")
        String projectGuid,

        @Schema(description = "URL da miniatura enviada via `/media/upload` (opcional)")
        String thumbnailUrl
) {
}
