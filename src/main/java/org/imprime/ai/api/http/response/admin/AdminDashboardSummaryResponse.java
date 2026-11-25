package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "Consolidado de KPIs executivos do painel administrativo")
public record AdminDashboardSummaryResponse(
        @Schema(description = "Janela temporal selecionada (7d, 30d, 90d, all)")
        String range,
        @Schema(description = "GMV acumulado histórico da plataforma")
        BigDecimal gmvTotal,
        @Schema(description = "GMV no período selecionado")
        BigDecimal gmvPeriod,
        @Schema(description = "Receita total de taxas da plataforma")
        BigDecimal platformFeeRevenueTotal,
        @Schema(description = "Receita de taxas da plataforma no período selecionado")
        BigDecimal platformFeeRevenuePeriod,
        @Schema(description = "Total de pedidos atualmente em andamento")
        long ordersInProgress,
        @Schema(description = "Total de pedidos concluídos no período selecionado")
        long ordersCompletedPeriod,
        @Schema(description = "Total de tickets abertos ou em análise")
        long openTickets,
        @Schema(description = "Total de disputas abertas ou em análise")
        long openDisputes,
        @Schema(description = "Total de denúncias abertas ou em análise")
        long openReports,
        @Schema(description = "Quantidade de modelos 3D aguardando moderação")
        long pendingModels,
        @Schema(description = "Quantidade de Makers aguardando aprovação")
        long pendingMakers,
        @Schema(description = "Quantidade de solicitações de reembolso pendentes")
        long pendingRefunds,
        @Schema(description = "Quantidade de pedidos com SLA atrasado")
        long delayedSlaCount,
        @Schema(description = "Total de usuários ativos na plataforma")
        long totalUsers,
        @Schema(description = "Total de Makers aprovados e ativos")
        long activeMakers,
        @Schema(description = "Série temporal de GMV e taxas no período")
        List<DashboardSeriesPointResponse> series,
        @Schema(description = "Distribuição de pedidos ativos por código de status")
        Map<String, Long> ordersByStatus
) {
}
