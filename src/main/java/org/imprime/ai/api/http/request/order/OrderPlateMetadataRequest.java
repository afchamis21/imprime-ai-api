package org.imprime.ai.api.http.request.order;

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
        String paintingInstructions
) {
}
