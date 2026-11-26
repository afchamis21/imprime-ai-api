package org.imprime.ai.api.http.request.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReviewReplyRequest(
        @NotNull @Size(max = 2000) String comment,
        @Size(max = 4) List<@NotBlank @Size(max = 36) String> photoGuids
) {}
