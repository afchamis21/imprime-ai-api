package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.chat.ChatSummaryResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;

import java.util.List;

@Schema(description = "Visão executiva consolidada do Painel do Maker")
public record MakerDashboardResponse(
        @Schema(description = "Resumo público de identificação do estúdio")
        MakerBriefResponse makerInfo,
        @Schema(description = "KPIs financeiros e operacionais")
        MakerDashboardStatsDto stats,
        @Schema(description = "Chats/negociações ativas aguardando atenção")
        List<ChatSummaryResponse> activeChats,
        @Schema(description = "Pedidos mais recentes recebidos pelo Maker")
        List<OrderSummaryResponse> recentOrders,
        @Schema(description = "Resumo em tempo real da frota de impressoras")
        FleetSummaryDto fleetSummary
) {
}
