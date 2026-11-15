package org.imprime.ai.api.model.dto;

import java.util.List;

public record ChatDTO(
        String chatGuid,
        List<UserDTO> members,
        List<MessageDTO> messages
) {
}
