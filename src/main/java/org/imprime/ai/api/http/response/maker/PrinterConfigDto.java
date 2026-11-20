package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Configurações operacionais e de automação da impressora (credenciais sensíveis são mascaradas)")
public record PrinterConfigDto(
        @Schema(description = "Aceite automático de pedidos", example = "true")
        boolean autoAcceptOrders,
        @Schema(description = "Integração via API externa habilitada", example = "true")
        boolean apiEnabled,
        @Schema(description = "Indica se existe uma API key ativa configurada", example = "true")
        boolean apiKeyConfigured,
        @Schema(description = "Prefixo mascarado da API key para identificação visual", example = "iak_live_x1c_****")
        String apiKeyPrefix,
        @Schema(description = "Altura máxima de camada configurada em mm", example = "0.20")
        BigDecimal maxLayerHeightMm,
        @Schema(description = "Preenchimento padrão (%)", example = "20")
        int defaultInfillPercent,
        @Schema(description = "Duração máxima aceita por trabalho em horas", example = "36")
        int maxPrintTimeHours,
        @Schema(description = "GUIDs dos filamentos vinculados à impressora")
        List<String> linkedFilamentGuids,
        @Schema(description = "Notas operacionais")
        String notes
) {
}
