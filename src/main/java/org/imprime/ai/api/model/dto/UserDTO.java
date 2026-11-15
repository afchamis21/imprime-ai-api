package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.User;

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
        Boolean isMaker
) {
    public static UserDTO from(User user) {
        return new UserDTO(
                user.getGuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCompanyId() != null
        );
    }
}
