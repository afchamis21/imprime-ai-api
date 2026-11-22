package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Observação opcional para aprovação ou restauração administrativa")
public record AdminOptionalNoteRequest(
        @Size(max = 2000)
        String notes
) {
}
