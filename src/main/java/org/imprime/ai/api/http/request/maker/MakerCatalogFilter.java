package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Filtros de busca do catálogo público de Makers")
public record MakerCatalogFilter(
        String query,
        String specialty,
        Double maxDistanceKm,
        BigDecimal minRating,
        Integer maxDeadlineDays,
        BigDecimal maxPrice,
        Boolean onlyOnline,
        List<MaterialType> materials,
        Double latitude,
        Double longitude
) {
}
