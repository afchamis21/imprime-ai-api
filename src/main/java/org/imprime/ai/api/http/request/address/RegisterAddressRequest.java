package org.imprime.ai.api.http.request.address;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.MessageCd;

import java.util.regex.Pattern;

public record RegisterAddressRequest(
        String zipCode,
        String neighborhood,
        String city,
        String state,
        String country,
        String addressLine1,
        @Nullable String addressLine2
) implements ValidatedRequest {

    private static final Pattern ZIP_CODE_PATTERN =
            Pattern.compile("^\\d{5}-?\\d{3}$");

    private static final Pattern TEXT_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}\\s'.,-]+$");

    private static final Pattern STATE_PATTERN =
            Pattern.compile("^[A-Z]{2}$");

    @Nullable
    public MessageCd getInvalidReason() {
        if (zipCode == null) {
            return MessageCd.MISSING_ADDRESS_ZIP_CODE;
        }

        if (!ZIP_CODE_PATTERN.matcher(zipCode).matches()) {
            return MessageCd.INVALID_ADDRESS_ZIP_CODE;
        }

        if (neighborhood == null) {
            return MessageCd.MISSING_ADDRESS_NEIGHBORHOOD;
        }

        String normalizedNeighborhood = neighborhood.trim();

        if (normalizedNeighborhood.length() < 2 || normalizedNeighborhood.length() > 100) {
            return MessageCd.INVALID_ADDRESS_NEIGHBORHOOD_LENGTH;
        }

        if (!TEXT_PATTERN.matcher(normalizedNeighborhood).matches()) {
            return MessageCd.INVALID_ADDRESS_NEIGHBORHOOD;
        }

        if (city == null) {
            return MessageCd.MISSING_ADDRESS_CITY;
        }

        String normalizedCity = city.trim();

        if (normalizedCity.length() < 2 || normalizedCity.length() > 100) {
            return MessageCd.INVALID_ADDRESS_CITY_LENGTH;
        }

        if (!TEXT_PATTERN.matcher(normalizedCity).matches()) {
            return MessageCd.INVALID_ADDRESS_CITY;
        }

        if (state == null) {
            return MessageCd.MISSING_ADDRESS_STATE;
        }

        String normalizedState = state.trim();

        if (!STATE_PATTERN.matcher(normalizedState).matches()) {
            return MessageCd.INVALID_ADDRESS_STATE;
        }

        if (country == null) {
            return MessageCd.MISSING_ADDRESS_COUNTRY;
        }

        String normalizedCountry = country.trim();

        if (normalizedCountry.length() < 2 || normalizedCountry.length() > 100) {
            return MessageCd.INVALID_ADDRESS_COUNTRY_LENGTH;
        }

        if (!TEXT_PATTERN.matcher(normalizedCountry).matches()) {
            return MessageCd.INVALID_ADDRESS_COUNTRY;
        }

        if (addressLine1 == null) {
            return MessageCd.MISSING_ADDRESS_LINE_1;
        }

        String normalizedAddressLine1 = addressLine1.trim();

        if (normalizedAddressLine1.length() < 5 || normalizedAddressLine1.length() > 255) {
            return MessageCd.INVALID_ADDRESS_LINE_1_LENGTH;
        }

        if (!TEXT_PATTERN.matcher(normalizedAddressLine1).matches()) {
            return MessageCd.INVALID_ADDRESS_LINE_1;
        }

        if (addressLine2 != null) {
            String normalizedAddressLine2 = addressLine2.trim();

            if (normalizedAddressLine2.length() > 255) {
                return MessageCd.INVALID_ADDRESS_LINE_2_LENGTH;
            }

            if (!TEXT_PATTERN.matcher(normalizedAddressLine2).matches()) {
                return MessageCd.INVALID_ADDRESS_LINE_2;
            }
        }

        return null;
    }
}
