package org.imprime.ai.api.http.response.common;

import java.time.Instant;
import java.util.List;

public record ReviewReplyResponse(
        String guid,
        String authorGuid,
        String authorName,
        String authorAvatarUrl,
        String comment,
        Instant createdAt,
        List<String> imageUrls
) {}
