package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Conjunto modular de parâmetros e tarifas financeiras vigentes para orçamentos do Maker")
public record MakerQuoteSettingsBundleResponse(
        @Schema(description = "Indica se o estúdio já personalizou seus módulos no banco", example = "true")
        boolean customized,
        @Schema(description = "Data/hora da última atualização dos módulos", example = "2026-11-23T21:10:00Z")
        Instant updatedAt,
        @Schema(description = "Tarifas de energia, depreciação e pedido mínimo vinculadas à empresa")
        CompanyRatesSnapshot companyRates,
        @Schema(description = "Lista ordenada de módulos configuráveis")
        List<MakerQuoteModuleConfigResponse> modules
) {
    @Schema(description = "Snapshot das tarifas financeiras da empresa")
    public record CompanyRatesSnapshot(
            @Schema(description = "Tarifa de energia em BRL/kWh", example = "0.9200")
            BigDecimal kwhRate,
            @Schema(description = "Depreciação horária em BRL/hora", example = "2.50")
            BigDecimal hourlyDepreciationRate,
            @Schema(description = "Valor mínimo de pedido configurado no perfil em BRL", example = "20.00")
            BigDecimal minOrderValue
    ) {
    }
}
