package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Dados de uma sala de chat de cotação")
public record ChatDTO(
        @Schema(description = "GUID único do chat", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        String chatGuid,
        @Schema(description = "Lista de participantes do chat (comprador e membros da empresa Maker)")
        List<UserDTO> members,
        @Schema(description = "Histórico de mensagens do chat")
        List<MessageDTO> messages
) {
}
