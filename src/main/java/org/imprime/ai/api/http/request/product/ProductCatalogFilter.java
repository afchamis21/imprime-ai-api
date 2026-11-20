package org.imprime.ai.api.http.request.product;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Filtros de busca do catálogo público de produtos")
public record ProductCatalogFilter(
        String query,
        String category,
        String makerGuid,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating,
        List<MaterialType> materials,
        Boolean inStockOnly
) {
}
