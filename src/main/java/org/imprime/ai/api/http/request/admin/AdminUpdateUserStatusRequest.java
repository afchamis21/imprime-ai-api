package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.StatusCd;

@Schema(description = "Payload administrativo para alterar o status de um usuário (suspensão, ativação ou inativação)")
public record AdminUpdateUserStatusRequest(
        @NotNull(message = "O status de destino é obrigatório")
        StatusCd status,
        @Size(max = 500)
        String reason
) {
}
