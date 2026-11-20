package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Lançamento no extrato financeiro do Maker")
public record FinanceTransactionResponse(
        @Schema(description = "GUID único da transação")
        String guid,
        @Schema(description = "Data de competência do lançamento (YYYY-MM-DD)", example = "2026-05-19")
        LocalDate transactionDate,
        @Schema(description = "Tipo da transação", example = "SALE")
        FinanceTransactionType type,
        @Schema(description = "Descrição resumida do lançamento", example = "Pedido #PED-492 — Suporte de Headset")
        String title,
        @Schema(description = "Código de referência (pedido, NF, PIX)", example = "PED-492")
        String reference,
        @Schema(description = "Categoria do lançamento", example = "Venda Direta")
        String category,
        @Schema(description = "Valor em BRL (positivo para entradas, negativo para saídas)", example = "120.00")
        BigDecimal amount,
        @Schema(description = "Status financeiro da transação", example = "COMPLETED")
        FinanceTransactionStatus status,
        @Schema(description = "Data e hora de registro em UTC (ISO-8601)")
        Instant createdAt
) {
}
