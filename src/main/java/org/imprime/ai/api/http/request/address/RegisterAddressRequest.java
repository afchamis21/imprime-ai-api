package org.imprime.ai.api.http.request.address;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.annotations.Regex;
import org.imprime.ai.api.validator.annotations.Required;
import org.imprime.ai.api.validator.annotations.StringLength;
import org.imprime.ai.api.validator.reflection.ValidatorEngine;

@Slf4j
public record RegisterAddressRequest(

        @Required(message = MessageCd.MISSING_ADDRESS_ZIP_CODE)
        @Regex(regex = "^\\d{5}-?\\d{3}$", message = MessageCd.INVALID_ADDRESS_ZIP_CODE)
        String zipCode,

        @Required(message = MessageCd.MISSING_ADDRESS_NEIGHBORHOOD)
        @StringLength(minLength = 2, maxLength = 100, minLengthMessage = MessageCd.INVALID_ADDRESS_NEIGHBORHOOD_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_NEIGHBORHOOD_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]+$", message = MessageCd.INVALID_ADDRESS_NEIGHBORHOOD)
        String neighborhood,

        @Required(message = MessageCd.MISSING_ADDRESS_CITY)
        @StringLength(minLength = 2, maxLength = 100, minLengthMessage = MessageCd.INVALID_ADDRESS_CITY_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_CITY_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]+$", message = MessageCd.INVALID_ADDRESS_CITY)
        String city,

        @Required(message = MessageCd.MISSING_ADDRESS_STATE)
        @Regex(regex = "^[A-Z]{2}$", message = MessageCd.INVALID_ADDRESS_STATE)
        String state,

        @Required(message = MessageCd.MISSING_ADDRESS_COUNTRY)
        @StringLength(minLength = 2, maxLength = 100, minLengthMessage = MessageCd.INVALID_ADDRESS_COUNTRY_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_COUNTRY_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]+$", message = MessageCd.INVALID_ADDRESS_COUNTRY)
        String country,

        @Required(message = MessageCd.MISSING_ADDRESS_LINE_1)
        @StringLength(minLength = 5, maxLength = 255, minLengthMessage = MessageCd.INVALID_ADDRESS_LINE_1_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_LINE_1_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]+$", message = MessageCd.INVALID_ADDRESS_LINE_1)
        String addressLine1,

        @Nullable
        @StringLength(maxLength = 255, minLengthMessage = MessageCd.INVALID_ADDRESS_LINE_2_LENGTH, maxLengthMessage = MessageCd.INVALID_ADDRESS_LINE_2_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'.,-]+$", message = MessageCd.INVALID_ADDRESS_LINE_2)
        String addressLine2
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