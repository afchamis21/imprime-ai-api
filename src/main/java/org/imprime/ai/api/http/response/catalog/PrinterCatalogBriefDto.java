package org.imprime.ai.api.http.response.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterTechnology;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Resumo de modelo de impressora 3D do catálogo fixo")
public record PrinterCatalogBriefDto(
        String guid,
        String slug,
        String brand,
        String modelName,
        String displayName,
        BigDecimal buildVolumeXMm,
        BigDecimal buildVolumeYMm,
        BigDecimal buildVolumeZMm,
        boolean isEnclosed,
        boolean supportsMulticolor,
        String multicolorSystemName,
        int maxColorSlots,
        PrinterTechnology technology,
        List<MaterialType> supportedMaterials,
        String imageUrl
) {
}
