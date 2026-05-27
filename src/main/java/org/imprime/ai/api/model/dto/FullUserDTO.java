package org.imprime.ai.api.model.dto;

import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.User;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record FullUserDTO(
        String userGuid,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        Boolean isMaker,
        List<AddressDTO> addresses
) {
    public static FullUserDTO from(@NonNull User user, @NonNull List<Address> addresses) {
        return new FullUserDTO(
                user.getGuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCompanyId() != null,
                addresses.stream().map(AddressDTO::from).toList()
        );
    }
}
