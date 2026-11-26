package org.imprime.ai.api.http.request.maker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReorderMakerItemsRequest(
        @NotEmpty List<@NotBlank @Size(max = 36) String> itemGuids
) {
}
