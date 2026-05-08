package org.imprime.ai.api.http.response;

import org.imprime.ai.api.model.dto.TokenDTO;
import org.imprime.ai.api.model.dto.UserDTO;

public record AuthResponse(
        TokenDTO accessToken,
        TokenDTO refreshToken,
        UserDTO user
) {
}
