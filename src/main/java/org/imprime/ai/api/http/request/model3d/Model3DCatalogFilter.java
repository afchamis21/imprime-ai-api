package org.imprime.ai.api.http.request.model3d;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.ModelPricingFilter;

import java.util.List;

@Schema(description = "Filtros de busca do catálogo de modelos 3D")
public record Model3DCatalogFilter(
        String query,
        String category,
        ModelPricingFilter pricing,
        List<String> tags,
        Boolean customizableOnly
) {
}
