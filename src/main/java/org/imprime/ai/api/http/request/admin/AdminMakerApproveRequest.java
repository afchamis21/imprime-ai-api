package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload administrativo para aprovação de novo Maker")
public record AdminMakerApproveRequest(
        Boolean verified,
        @Size(max = 2000)
        String notes
) {
}
