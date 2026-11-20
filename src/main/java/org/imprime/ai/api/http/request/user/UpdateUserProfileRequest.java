package org.imprime.ai.api.http.request.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados cadastrais atualizáveis do perfil do usuário autenticado (endereços possuem CRUD próprio em `/users/me/addresses`)")
public record UpdateUserProfileRequest(
        @Schema(description = "Primeiro nome do usuário", example = "Lucas", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O primeiro nome é obrigatório")
        @Size(min = 2, max = 100, message = "O primeiro nome deve ter entre 2 e 100 caracteres")
        String firstName,

        @Schema(description = "Sobrenome do usuário", example = "Amorim", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O sobrenome é obrigatório")
        @Size(min = 2, max = 100, message = "O sobrenome deve ter entre 2 e 100 caracteres")
        String lastName,

        @Schema(description = "Endereço de e-mail", example = "lucas@imprime.ai", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String email,

        @Schema(description = "Telefone de contato", example = "11987654321", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O telefone é obrigatório")
        @Size(min = 8, max = 20, message = "O telefone deve ter entre 8 e 20 caracteres")
        String phoneNumber
) {
}
