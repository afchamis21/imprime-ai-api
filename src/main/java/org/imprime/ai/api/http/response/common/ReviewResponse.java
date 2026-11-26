package org.imprime.ai.api.http.response.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Dados de uma avaliação publicada")
public record ReviewResponse(
        @Schema(description = "GUID único da avaliação", example = "8a2f7f10-3c90-4a12-b89e-111122223333")
        String guid,
        @Schema(description = "GUID do autor da avaliação", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        String authorGuid,
        @Schema(description = "Nome do autor", example = "Lucas Amorim")
        String authorName,
        @Schema(description = "URL do avatar do autor")
        String authorAvatarUrl,
        @Schema(description = "Nota de 1 a 5", example = "5")
        int rating,
        @Schema(description = "Texto da avaliação", example = "Muito resistente e ótimo acabamento!")
        String comment,
        @Schema(description = "Data e hora de criação em UTC (ISO-8601)", example = "2026-11-19T18:30:00Z")
        Instant createdAt,
        List<String> imageUrls,
        long likeCount,
        boolean liked,
        List<ReviewReplyResponse> replies
) {
}
