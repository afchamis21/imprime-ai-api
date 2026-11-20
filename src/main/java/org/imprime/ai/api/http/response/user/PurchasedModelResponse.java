package org.imprime.ai.api.http.response.user;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.DimensionsDto;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Modelo 3D digital adquirido na biblioteca do usuário")
public record PurchasedModelResponse(
        @Schema(description = "GUID do registro de compra", example = "11111111-2222-3333-4444-555555555555")
        String purchaseGuid,
        @Schema(description = "GUID do modelo 3D no catálogo", example = "7f9c24ef-1000-4000-8000-000000000001")
        String modelGuid,
        @Schema(description = "Slug do modelo 3D", example = "dragao-articulado-imperial-v2")
        String slug,
        @Schema(description = "Título do modelo", example = "Dragão Articulado Imperial V2")
        String title,
        @Schema(description = "Nome do autor/designer", example = "DracoForge 3D")
        String designerName,
        @Schema(description = "Formato do arquivo 3D", example = "THREE_MF")
        FileFormat3D format,
        @Schema(description = "Tamanho do arquivo em bytes", example = "44879052")
        Long fileSizeBytes,
        @Schema(description = "Dimensões físicas em milímetros")
        DimensionsDto dimensions,
        @Schema(description = "Data da compra em UTC (ISO-8601)", example = "2026-05-14T10:30:00Z")
        Instant purchasedAt,
        @Schema(description = "Valor pago em BRL", example = "34.90")
        BigDecimal pricePaid,
        @Schema(description = "Indica se permite customização paramétrica OpenSCAD", example = "false")
        boolean customizable,
        @Schema(description = "Licença adquirida", example = "PERSONAL")
        LicenseType license,
        @Schema(description = "Quantidade de downloads já efetuados", example = "3")
        int downloadsCount,
        @Schema(description = "Material recomendado para impressão", example = "PLA")
        MaterialType recommendedMaterial,
        @Schema(description = "URL da imagem de capa do modelo")
        String coverImageUrl
) {
}
