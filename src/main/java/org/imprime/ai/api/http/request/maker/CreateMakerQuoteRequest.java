package org.imprime.ai.api.http.request.maker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.QuoteHistoryStatus;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record CreateMakerQuoteRequest(
        @NotBlank(message = "Título do orçamento é obrigatório")
        @Size(max = 180, message = "Título do orçamento deve ter no máximo 180 caracteres")
        String title,

        @Size(max = 120, message = "Nome do cliente deve ter no máximo 120 caracteres")
        String clientName,

        QuoteHistoryStatus quoteStatus,

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade deve ser maior ou igual a 1")
        Integer quantity,

        @NotNull(message = "Valor unitário é obrigatório")
        @DecimalMin(value = "0.00", message = "Valor unitário não pode ser negativo")
        BigDecimal unitPrice,

        @NotNull(message = "Valor total é obrigatório")
        @DecimalMin(value = "0.00", message = "Valor total não pode ser negativo")
        BigDecimal finalPriceTotal,

        @NotNull(message = "Custo total de produção é obrigatório")
        @DecimalMin(value = "0.00", message = "Custo total de produção não pode ser negativo")
        BigDecimal productionCostTotal,

        @NotNull(message = "Lucro líquido estimado é obrigatório")
        BigDecimal estimatedProfitTotal,

        @NotNull(message = "Snapshot de entrada é obrigatório")
        JsonNode inputSnapshot,

        @NotNull(message = "Snapshot de parâmetros é obrigatório")
        JsonNode settingsSnapshot,

        @NotNull(message = "Snapshot de resultado é obrigatório")
        JsonNode resultSnapshot
) {
}
