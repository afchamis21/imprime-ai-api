package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.SupportTicketStatus;

import java.util.List;

@Schema(description = "Resposta oficial da equipe administrativa em um ticket ou disputa")
public record AdminTicketReplyRequest(
        @Schema(description = "Conteúdo da resposta enviada ao usuário", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A mensagem de resposta é obrigatória")
        @Size(max = 4000, message = "A mensagem deve ter no máximo 4000 caracteres")
        String message,

        @Schema(description = "URLs opcionais de anexos/evidências")
        List<String> attachments,

        @Schema(description = "Próximo status opcional após envio da resposta (padrão: WAITING_USER quando OPEN/IN_REVIEW)")
        SupportTicketStatus nextStatus
) {
}
