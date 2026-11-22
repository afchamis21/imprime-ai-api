package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum AuditAction implements CodeAttribute {
    ADMIN_LOGIN("ADMIN_LOGIN"),
    ADMIN_LOGOUT("ADMIN_LOGOUT"),
    USER_CREATED("USER_CREATED"),
    USER_UPDATED("USER_UPDATED"),
    USER_SUSPENDED("USER_SUSPENDED"),
    USER_REACTIVATED("USER_REACTIVATED"),
    USER_DELETED("USER_DELETED"),
    MODEL_APPROVED("MODEL_APPROVED"),
    MODEL_REJECTED("MODEL_REJECTED"),
    MODEL_DMCA_TAKEDOWN("MODEL_DMCA_TAKEDOWN"),
    MODEL_RESTORED("MODEL_RESTORED"),
    MAKER_APPROVED("MAKER_APPROVED"),
    MAKER_REJECTED("MAKER_REJECTED"),
    MAKER_SUSPENDED("MAKER_SUSPENDED"),
    MAKER_REACTIVATED("MAKER_REACTIVATED"),
    MAKER_VERIFIED_UPDATED("MAKER_VERIFIED_UPDATED"),
    PRODUCT_STATUS_UPDATED("PRODUCT_STATUS_UPDATED"),
    ORDER_CANCELLED("ORDER_CANCELLED"),
    REFUND_CREATED("REFUND_CREATED"),
    REFUND_APPROVED("REFUND_APPROVED"),
    REFUND_REJECTED("REFUND_REJECTED"),
    TICKET_REPLIED("TICKET_REPLIED"),
    TICKET_UPDATED("TICKET_UPDATED"),
    PAYOUT_COMPLETED("PAYOUT_COMPLETED"),
    PAYOUT_FAILED("PAYOUT_FAILED");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<AuditAction> {
        public Converter() {
            super(AuditAction.class);
        }
    }
}
