package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Arquivo 3D/fatiado armazenado no Print Archive do Maker")
public record ArchiveFileResponse(
        @Schema(description = "GUID único do arquivo no acervo")
        String guid,
        @Schema(description = "Nome técnico do arquivo", example = "prototipo_arquitetonico_torre_a.3mf")
        String fileName,
        @Schema(description = "Título legível no acervo", example = "Protótipo Arquitetônico — Torre A")
        String displayName,
        @Schema(description = "Formato do arquivo 3D", example = "THREE_MF")
        FileFormat3D format,
        @Schema(description = "Tamanho do arquivo em bytes", example = "19293798")
        long fileSizeBytes,
        @Schema(description = "URL da miniatura")
        String thumbnailUrl,
        @Schema(description = "Material previsto", example = "PLA")
        MaterialType material,
        @Schema(description = "Nome da cor", example = "Branco")
        String colorName,
        @Schema(description = "Código hexadecimal da cor", example = "#f1f5f9")
        String colorHex,
        @Schema(description = "GUID do rolo de filamento associado")
        String filamentGuid,
        @Schema(description = "Peso estimado em gramas", example = "185.0")
        BigDecimal estimatedWeightGrams,
        @Schema(description = "Tempo estimado de impressão em minutos", example = "260")
        int estimatedTimeMinutes,
        @Schema(description = "Altura de camada em mm", example = "0.16")
        BigDecimal layerHeightMm,
        @Schema(description = "Preenchimento (%)", example = "15")
        int infillPercent,
        @Schema(description = "Quantidade de mesas/placas", example = "3")
        int platesCount,
        @Schema(description = "Quantidade de impressões já realizadas a partir deste arquivo", example = "2")
        int printCount,
        @Schema(description = "Data da última impressão em UTC (ISO-8601)")
        Instant lastPrintedAt,
        @Schema(description = "Data de criação no acervo em UTC (ISO-8601)")
        Instant createdAt,
        @Schema(description = "Tags organizacionais")
        List<String> tags,
        @Schema(description = "Origem do arquivo")
        String sourceName,
        @Schema(description = "GUID do projeto multi-peça vinculado (opcional)")
        String projectGuid,
        @Schema(description = "Custo estimado de produção em BRL", example = "26.80")
        BigDecimal estimatedCost
) {
}
