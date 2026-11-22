package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados obrigatórios para derrubada de modelo 3D por denúncia ou notificação DMCA")
public record AdminDmcaTakedownRequest(
        @NotBlank(message = "O motivo da denúncia/DMCA é obrigatório")
        @Size(max = 2000)
        String reason,
        @NotBlank(message = "O requerente da notificação DMCA é obrigatório")
        @Size(max = 200)
        String claimant,
        @NotBlank(message = "O protocolo/referência da denúncia é obrigatório")
        @Size(max = 120)
        String reference
) {
}
