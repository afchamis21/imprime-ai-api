package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.jspecify.annotations.NonNull;

import java.util.List;

@Schema(description = "Perfil completo do usuário incluindo seus endereços")
public record FullUserDTO(
        @Schema(description = "GUID único do usuário", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        String userGuid,
        @Schema(description = "Primeiro nome", example = "Lucas")
        String firstName,
        @Schema(description = "Sobrenome", example = "Amorim")
        String lastName,
        @Schema(description = "Endereço de e-mail", example = "lucas@imprime.ai")
        String email,
        @Schema(description = "Telefone de contato", example = "11987654321")
        String phoneNumber,
        @Schema(description = "Indica se o usuário possui empresa associada como Maker", example = "false")
        Boolean isMaker,
        @Schema(description = "URL da foto de perfil do usuário")
        String avatarUrl,
        @Schema(description = "Lista de endereços cadastrados para o usuário")
        List<AddressDTO> addresses,
        @Schema(description = "Perfil de acesso do usuário (BUYER, DESIGNER, MAKER, ADMIN)", example = "BUYER")
        UserRole role,
        @Schema(description = "Status cadastral do usuário", example = "ACTIVE")
        StatusCd status,
        @Schema(description = "Indica se o e-mail do usuário foi verificado", example = "false")
        Boolean emailVerified
) {
    public FullUserDTO(
            String userGuid,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            Boolean isMaker,
            String avatarUrl,
            List<AddressDTO> addresses,
            UserRole role,
            StatusCd status
    ) {
        this(userGuid, firstName, lastName, email, phoneNumber, isMaker, avatarUrl, addresses, role, status, false);
    }

    public static FullUserDTO from(@NonNull User user, @NonNull List<Address> addresses) {
        UserRole resolvedRole = user.getRole() != null
                ? user.getRole()
                : (user.getCompanyId() != null ? UserRole.MAKER : UserRole.BUYER);
        StatusCd resolvedStatus = user.getStatus() != null ? user.getStatus() : StatusCd.ACTIVE;
        return new FullUserDTO(
                user.getGuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCompanyId() != null,
                user.getAvatarUrl(),
                addresses.stream().map(AddressDTO::from).toList(),
                resolvedRole,
                resolvedStatus,
                user.isEmailVerified()
        );
    }
}
