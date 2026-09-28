package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum OrderStatusCd implements CodeAttribute {
    CREATED("1.00"),
    QUOTING("1.01"),
    CONFIRMED("2.00"),
    IN_PRODUCTION("2.01"),
    IN_TRANSIT("2.02"),
    COMPLETED("3.00"),
    CANCELLED("9.00"),
    ERROR("9.01");

    private final String code;

    public static class Converter extends CodeAttributeConverter<OrderStatusCd> {
        public Converter() {
            super(OrderStatusCd.class);
        }
    }
}
