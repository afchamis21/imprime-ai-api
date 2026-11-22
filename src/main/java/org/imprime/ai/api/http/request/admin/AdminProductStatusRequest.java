package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.StatusCd;

@Schema(description = "Payload administrativo para moderar disponibilidade/status de produto do catálogo")
public record AdminProductStatusRequest(
        StatusCd status,
        Boolean inStock,
        @Size(max = 2000)
        String reason
) {
}
