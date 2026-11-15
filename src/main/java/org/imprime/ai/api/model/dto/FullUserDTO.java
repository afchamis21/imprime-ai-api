package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.User;
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
        @Schema(description = "Lista de endereços cadastrados para o usuário")
        List<AddressDTO> addresses
) {
    public static FullUserDTO from(@NonNull User user, @NonNull List<Address> addresses) {
        return new FullUserDTO(
                user.getGuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCompanyId() != null,
                addresses.stream().map(AddressDTO::from).toList()
        );
    }
}
