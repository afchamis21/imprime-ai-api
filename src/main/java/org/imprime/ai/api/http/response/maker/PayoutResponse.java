package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.PixKeyType;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Comprovante de solicitação de saque via PIX")
public record PayoutResponse(
        @Schema(description = "GUID do saque solicitado")
        String payoutGuid,
        @Schema(description = "GUID da transação gerada no extrato")
        String transactionGuid,
        @Schema(description = "Valor solicitado em BRL", example = "500.00")
        BigDecimal amount,
        @Schema(description = "Tipo da chave PIX", example = "EMAIL")
        PixKeyType pixKeyType,
        @Schema(description = "Chave PIX de destino mascarada", example = "fin***@studio3d.com.br")
        String pixKeyMasked,
        @Schema(description = "Status do processamento do saque", example = "PROCESSING")
        FinanceTransactionStatus status,
        @Schema(description = "Saldo disponível restante após o débito em BRL", example = "180.00")
        BigDecimal remainingAvailableBalance,
        @Schema(description = "Data e hora da solicitação em UTC (ISO-8601)")
        Instant requestedAt
) {
}
