package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.UserRole;

@Schema(description = "Payload administrativo para criação de um novo usuário")
public record AdminCreateUserRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100)
        String firstName,
        @NotBlank(message = "Sobrenome é obrigatório")
        @Size(max = 100)
        String lastName,
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,
        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, max = 100, message = "A senha deve ter no mínimo 6 caracteres")
        String password,
        @NotNull(message = "Tipo de documento é obrigatório")
        DocumentType documentType,
        @NotBlank(message = "Documento é obrigatório")
        @Size(max = 20)
        String document,
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
