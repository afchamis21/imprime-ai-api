package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;

import java.time.Instant;

@Schema(description = "Resumo administrativo de um usuário da plataforma")
public record AdminUserSummaryResponse(
        String userGuid,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phoneNumber,
        DocumentType documentType,
        String documentMasked,
        UserRole role,
        StatusCd status,
        boolean isMaker,
        String companyGuid,
        String companyName,
        String avatarUrl,
        String suspensionReason,
        Instant suspendedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
