package org.imprime.ai.api.http.request.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para atualização parcial (PATCH) de um endereço")
public record UpdateAddressRequest(
        @Schema(description = "CEP no formato `00000-000` ou `00000000`", example = "01403-000")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido")
        String zipCode,

        @Schema(description = "Bairro", example = "Jardim Paulista")
        @Size(min = 2, max = 100, message = "O bairro deve ter entre 2 e 100 caracteres")
        String neighborhood,

        @Schema(description = "Cidade", example = "São Paulo")
        @Size(min = 2, max = 100, message = "A cidade deve ter entre 2 e 100 caracteres")
        String city,

        @Schema(description = "Sigla da UF com 2 letras maiúsculas", example = "SP")
        @Pattern(regexp = "^[A-Z]{2}$", message = "UF deve conter 2 letras maiúsculas")
        String state,

        @Schema(description = "País", example = "Brasil")
        @Size(min = 2, max = 100, message = "O país deve ter entre 2 e 100 caracteres")
        String country,

        @Schema(description = "Logradouro e número", example = "Rua das Flores, 200")
        @Size(min = 5, max = 255, message = "O logradouro deve ter entre 5 e 255 caracteres")
        String addressLine1,

        @Schema(description = "Complemento do endereço", example = "Bloco B")
        @Size(max = 255, message = "O complemento deve ter no máximo 255 caracteres")
        String addressLine2,

        @Schema(description = "Latitude geográfica", example = "-23.5649")
        Double latitude,

        @Schema(description = "Longitude geográfica", example = "-46.6512")
        Double longitude
) {
}
