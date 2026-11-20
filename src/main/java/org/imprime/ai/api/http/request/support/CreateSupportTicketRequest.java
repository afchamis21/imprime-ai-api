package org.imprime.ai.api.http.request.support;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.SupportTicketCategory;

@Schema(description = "Dados para abertura de um novo chamado de suporte")
public record CreateSupportTicketRequest(
        @Schema(description = "Assunto do chamado", example = "Problema com arquivo STL do pedido PED-1029", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O assunto é obrigatório")
        @Size(max = 150, message = "O assunto deve ter no máximo 150 caracteres")
        String subject,

        @Schema(description = "Categoria do atendimento", example = "ORDER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A categoria é obrigatória")
        SupportTicketCategory category,

        @Schema(description = "GUID do pedido relacionado (opcional)")
        String orderGuid,

        @Schema(description = "Mensagem inicial descrevendo a dúvida ou ocorrência", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A mensagem inicial é obrigatória")
        @Size(max = 4000, message = "A mensagem deve ter no máximo 4000 caracteres")
        String message
) {
}
