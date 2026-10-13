package org.imprime.ai.api.model.dto;

import java.math.BigDecimal;

public record CompanyChoiceDTO(
        String name, AddressDTO address, Distance distance
) {
    public record Distance(BigDecimal value, String unit) {}
}
