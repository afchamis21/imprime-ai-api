package org.imprime.ai.api.model.dto;

import java.math.BigDecimal;

public record CompanyChoiceDTO(
        String name, AddressDTO address
) {
    public record Distance(BigDecimal value, String unit) {} // TODO This can be improved
}
