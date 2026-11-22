package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Consolidado financeiro administrativo da plataforma")
public record AdminFinanceSummaryResponse(
        BigDecimal gmvTotal,
        BigDecimal platformFeeRevenue,
        BigDecimal escrowBalance,
        long escrowOrdersCount,
        BigDecimal makerPayoutsPaid,
        BigDecimal makerPayoutsPending,
        BigDecimal designerPayoutsPaid,
        BigDecimal designerPayoutsPending,
        BigDecimal totalPayoutsPaid,
        BigDecimal totalPayoutsPending,
        long pendingPayoutsCount,
        BigDecimal refundsTotal,
        BigDecimal netPlatformRevenue
) {
}
