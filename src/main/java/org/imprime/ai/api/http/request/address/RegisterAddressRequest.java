package org.imprime.ai.api.http.request.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.annotations.Regex;
import org.imprime.ai.api.validator.annotations.Required;
import org.imprime.ai.api.validator.annotations.StringLength;
import org.imprime.ai.api.validator.reflection.ValidatorEngine;

@Slf4j
@Schema(description = "Dados para cadastro de endereço")
public record RegisterAddressRequest(

        @Schema(description = "CEP no formato `00000-000` ou `00000000`", example = "01310-100", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_ADDRESS_ZIP_CODE)
        @Regex(regex = "^\\d{5}-?\\d{3}$", message = MessageCd.INVALID_ADDRESS_ZIP_CODE)
        String zipCode,

        @Schema(description = "Bairro (2 a 100 caracteres)", example = "Bela Vista")
        @StringLength(minLength = 2, maxLength = 100, minLengthMessage = MessageCd.INVALID_ADDRESS_NEIGHBORHOOD_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_NEIGHBORHOOD_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,/-]+$", message = MessageCd.INVALID_ADDRESS_NEIGHBORHOOD)
        String neighborhood,

        @Schema(description = "Cidade (2 a 100 caracteres)", example = "São Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_ADDRESS_CITY)
        @StringLength(minLength = 2, maxLength = 100, minLengthMessage = MessageCd.INVALID_ADDRESS_CITY_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_CITY_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]+$", message = MessageCd.INVALID_ADDRESS_CITY)
        String city,

        @Schema(description = "Sigla da UF com 2 letras maiúsculas", example = "SP", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_ADDRESS_STATE)
        @Regex(regex = "^[A-Z]{2}$", message = MessageCd.INVALID_ADDRESS_STATE)
        String state,

        @Schema(description = "País (2 a 100 caracteres)", example = "Brasil", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_ADDRESS_COUNTRY)
        @StringLength(minLength = 2, maxLength = 100, minLengthMessage = MessageCd.INVALID_ADDRESS_COUNTRY_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_COUNTRY_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]+$", message = MessageCd.INVALID_ADDRESS_COUNTRY)
        String country,

        @Schema(description = "Logradouro e número (5 a 255 caracteres)", example = "Av. Paulista, 1106", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_ADDRESS_LINE_1)
        @StringLength(minLength = 5, maxLength = 255, minLengthMessage = MessageCd.INVALID_ADDRESS_LINE_1_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_LINE_1_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]+$", message = MessageCd.INVALID_ADDRESS_LINE_1)
        String addressLine1,

        @Schema(description = "Complemento do endereço (opcional, até 255 caracteres)", example = "Andar 7")
        @Nullable
        @StringLength(maxLength = 255, minLengthMessage = MessageCd.INVALID_ADDRESS_LINE_2_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_LINE_2_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]*$", message = MessageCd.INVALID_ADDRESS_LINE_2)
        String addressLine2,

        @Schema(description = "Latitude geográfica do endereço", example = "-23.5641")
        Double latitude,
        @Schema(description = "Longitude geográfica do endereço", example = "-46.6524")
        Double longitude
) implements ValidatedRequest {

    @Nullable
    @Override
    public MessageCd getInvalidReason() {
        try {
            MessageCd messageCd = ValidatorEngine.validate(this);
            if (messageCd != null) {
                return messageCd;
            }
        } catch (Exception e) {
            log.error("Error running reflective validations for Address Request!", e);
        }

        return null;
    }
}