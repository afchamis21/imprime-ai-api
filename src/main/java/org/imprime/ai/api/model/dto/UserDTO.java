package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;

@Schema(description = "Dados básicos de identificação de um usuário")
public record UserDTO(
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
        @Schema(description = "Indica se o usuário é um Maker", example = "false")
        Boolean isMaker,
        @Schema(description = "URL da foto de perfil do usuário")
        String avatarUrl,
        @Schema(description = "Perfil de acesso do usuário (BUYER, DESIGNER, MAKER, ADMIN)", example = "BUYER")
        UserRole role,
        @Schema(description = "Status cadastral do usuário", example = "ACTIVE")
        StatusCd status,
        @Schema(description = "Indica se o e-mail do usuário foi verificado", example = "false")
        Boolean emailVerified
) {
    public UserDTO(
            String userGuid,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            Boolean isMaker,
            String avatarUrl,
            UserRole role,
            StatusCd status
    ) {
        this(userGuid, firstName, lastName, email, phoneNumber, isMaker, avatarUrl, role, status, false);
    }

    public static UserDTO from(User user) {
        UserRole resolvedRole = user.getRole() != null
                ? user.getRole()
                : (user.getCompanyId() != null ? UserRole.MAKER : UserRole.BUYER);
        StatusCd resolvedStatus = user.getStatus() != null ? user.getStatus() : StatusCd.ACTIVE;
        return new UserDTO(
                user.getGuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCompanyId() != null,
                user.getAvatarUrl(),
                resolvedRole,
                resolvedStatus,
                user.isEmailVerified()
        );
    }
}
