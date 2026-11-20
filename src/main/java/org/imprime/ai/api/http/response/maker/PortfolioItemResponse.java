package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Item publicado no portfólio de um Maker")
public record PortfolioItemResponse(
        @Schema(description = "GUID único do item de portfólio", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String guid,
        @Schema(description = "Título do trabalho realizado", example = "Busto do Batman")
        String title,
        @Schema(description = "Material utilizado", example = "RESIN")
        MaterialType material,
        @Schema(description = "URL da imagem principal de capa")
        String coverImageUrl,
        @Schema(description = "Lista de URLs das imagens da galeria")
        List<String> imageUrls,
        @Schema(description = "Descrição técnica do acabamento e processo")
        String description,
        @Schema(description = "Tempo total de impressão em minutos", example = "465")
        Integer printTimeMinutes,
        @Schema(description = "Modelo da impressora utilizada", example = "Bambu Lab X1 Carbon")
        String printerUsed,
        @Schema(description = "Altura de camada em milímetros", example = "0.05")
        BigDecimal layerHeightMm,
        @Schema(description = "Tags de categorização", example = "[\"Colecionável\", \"Resina 8K\"]")
        List<String> tags,
        @Schema(description = "Data de criação em UTC (ISO-8601)", example = "2026-05-14T18:00:00Z")
        Instant createdAt
) {
}
