package org.imprime.ai.api.http.request.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderPlateMetadataRequest(
        Integer plateNumber,
        String plateName,
        String material,
        String colorHex,
        Integer infillPercent,
        Boolean supports,
        Double scaleX,
        Double scaleY,
        Double scaleZ,
        Boolean isMultiColor,
        String paintingInstructions,
        BigDecimal dimensionXMm,
        BigDecimal dimensionYMm,
        BigDecimal dimensionZMm,
        Integer colorCount,
        BigDecimal layerHeightMm,
        String urgency,
        Integer quantity,
        Integer wallCount,
        String infillPattern,
        String adhesionType,
        String supportType,
        String finish,
        String tolerance,
        String notes,
        List<String> colors,
        BigDecimal estimatedWeightGrams,
        Integer estimatedPrintTimeMinutes,
        BigDecimal estimatedPriceBrl
) {
    public OrderPlateMetadataRequest(
            Integer plateNumber,
            String plateName,
            String material,
            String colorHex,
            Integer infillPercent,
            Boolean supports,
            Double scaleX,
            Double scaleY,
            Double scaleZ,
            Boolean isMultiColor,
            String paintingInstructions
    ) {
        this(
                plateNumber, plateName, material, colorHex, infillPercent, supports,
                scaleX, scaleY, scaleZ, isMultiColor, paintingInstructions,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    public OrderPlateMetadataRequest(
            Integer plateNumber,
            String plateName,
            String material,
            String colorHex,
            Integer infillPercent,
            Boolean supports,
            Double scaleX,
            Double scaleY,
            Double scaleZ,
            Boolean isMultiColor,
            String paintingInstructions,
            BigDecimal dimensionXMm,
            BigDecimal dimensionYMm,
            BigDecimal dimensionZMm,
            Integer colorCount,
            BigDecimal layerHeightMm,
            String urgency
    ) {
        this(
                plateNumber, plateName, material, colorHex, infillPercent, supports,
                scaleX, scaleY, scaleZ, isMultiColor, paintingInstructions,
                dimensionXMm, dimensionYMm, dimensionZMm, colorCount, layerHeightMm, urgency,
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }
}
