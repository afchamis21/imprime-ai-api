package org.imprime.ai.api.model.dto;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.model.Address;

public record AddressDTO(
        String guid,
        String zipCode,
        String neighborhood,
        String city,
        String state,
        String country,
        String addressLine1,
        @Nullable String addressLine2
) {
    public static AddressDTO from(Address address) {
        return new AddressDTO(
                address.getGuid(),
                address.getZipCode(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.getAddressLine1(),
                address.getAddressLine2()
        );
    }
}
