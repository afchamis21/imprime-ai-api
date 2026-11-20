package org.imprime.ai.api.http.request.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criação ou substituição completa de um endereço")
public record CreateAddressRequest(
        @Schema(description = "CEP no formato `00000-000` ou `00000000`", example = "01403-000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O CEP é obrigatório")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido")
        String zipCode,

        @Schema(description = "Bairro", example = "Jardim Paulista", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O bairro é obrigatório")
        @Size(min = 2, max = 100, message = "O bairro deve ter entre 2 e 100 caracteres")
        String neighborhood,

        @Schema(description = "Cidade", example = "São Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A cidade é obrigatória")
        @Size(min = 2, max = 100, message = "A cidade deve ter entre 2 e 100 caracteres")
        String city,

        @Schema(description = "Sigla da UF com 2 letras maiúsculas", example = "SP", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O estado (UF) é obrigatório")
        @Pattern(regexp = "^[A-Z]{2}$", message = "UF deve conter 2 letras maiúsculas")
        String state,

        @Schema(description = "País", example = "Brasil", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O país é obrigatório")
        @Size(min = 2, max = 100, message = "O país deve ter entre 2 e 100 caracteres")
        String country,

        @Schema(description = "Logradouro e número", example = "Rua das Flores, 142", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O logradouro é obrigatório")
        @Size(min = 5, max = 255, message = "O logradouro deve ter entre 5 e 255 caracteres")
        String addressLine1,

        @Schema(description = "Complemento do endereço (opcional)", example = "Apto 5")
        @Size(max = 255, message = "O complemento deve ter no máximo 255 caracteres")
        String addressLine2,

        @Schema(description = "Latitude geográfica", example = "-23.5649")
        Double latitude,

        @Schema(description = "Longitude geográfica", example = "-46.6512")
        Double longitude,

        @Schema(description = "Define este endereço como padrão após a criação", example = "false")
        Boolean defaultAddress
) {
}
