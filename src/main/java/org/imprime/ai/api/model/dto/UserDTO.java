package org.imprime.ai.api.model.dto;

import org.imprime.ai.api.model.User;

public record UserDTO(
        String userGuid,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        Boolean isMaker
) {
    public static UserDTO from(User user) {
        return new UserDTO(
                user.getGuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCompanyId() != null
        );
    }
}
