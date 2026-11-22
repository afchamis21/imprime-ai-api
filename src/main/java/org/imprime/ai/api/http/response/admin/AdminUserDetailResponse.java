package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;

import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhamento administrativo completo de um usuário")
public record AdminUserDetailResponse(
        String userGuid,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phoneCountry,
        String phoneNumber,
        DocumentType documentType,
        String document,
        UserRole role,
        StatusCd status,
        boolean isMaker,
        String companyGuid,
        String companyName,
        String avatarUrl,
        String suspensionReason,
        Instant suspendedAt,
        List<AddressDTO> addresses,
        long ordersCount,
        long modelsCount,
        long ticketsCount,
        Instant createdAt,
        Instant updatedAt
) {
}
