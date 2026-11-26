package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.imprime.ai.api.model.Address;

@Schema(description = "Dados de um endereço cadastrado")
public record AddressDTO(
        @Schema(description = "GUID único do endereço", example = "123e4567-e89b-12d3-a456-426614174000")
        String guid,
        @Schema(description = "CEP", example = "01310-100")
        String zipCode,
        @Schema(description = "Bairro", example = "Bela Vista")
        String neighborhood,
        @Schema(description = "Cidade", example = "São Paulo")
        String city,
        @Schema(description = "Sigla da UF", example = "SP")
        String state,
        @Schema(description = "País", example = "Brasil")
        String country,
        @Schema(description = "Logradouro e número", example = "Av. Paulista, 1106")
        String addressLine1,
        @Schema(description = "Complemento do endereço (opcional)", example = "Andar 7")
        @Nullable String addressLine2,
        @Schema(description = "Latitude do endereço (opcional)", example = "-23.5614")
        @Nullable Double latitude,
        @Schema(description = "Longitude do endereço (opcional)", example = "-46.6559")
        @Nullable Double longitude,
        @Schema(description = "Indica se este é o endereço padrão do usuário")
        boolean defaultAddress
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
                address.getAddressLine2(),
                address.getLatitude(),
                address.getLongitude(),
                Boolean.TRUE.equals(address.getDefaultAddress())
        );
    }
}
