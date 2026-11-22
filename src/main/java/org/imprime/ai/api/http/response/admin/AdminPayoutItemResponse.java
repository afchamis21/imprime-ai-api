package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.AdminPayoutBeneficiaryType;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.PixKeyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Registro unificado de repasse (payout) para Maker ou Designer")
public record AdminPayoutItemResponse(
        String payoutGuid,
        AdminPayoutBeneficiaryType beneficiaryType,
        String beneficiaryGuid,
        String beneficiaryName,
        String beneficiaryEmail,
        BigDecimal amount,
        FinanceTransactionStatus status,
        LocalDate transactionDate,
        String title,
        String reference,
        PixKeyType pixKeyType,
        String pixKeyMasked,
        String failureReason,
        String processedByGuid,
        String processedByName,
        Instant processedAt,
        Instant createdAt
) {
}
