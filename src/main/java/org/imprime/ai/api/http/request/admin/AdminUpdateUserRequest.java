package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.UserRole;

@Schema(description = "Payload administrativo para atualização cadastral de usuário")
public record AdminUpdateUserRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100)
        String firstName,
        @NotBlank(message = "Sobrenome é obrigatório")
        @Size(max = 100)
        String lastName,
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,
        @NotBlank(message = "Telefone é obrigatório")
        @Size(max = 20)
        String phoneNumber,
        @Size(max = 5)
        String phoneCountry,
        @NotNull(message = "Perfil de acesso é obrigatório")
        UserRole role,
        @Size(max = 1000)
        String avatarUrl
) {
}
