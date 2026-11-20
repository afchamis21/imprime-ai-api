package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.PixKeyType;

import java.math.BigDecimal;

@Schema(description = "Dados para solicitação de saque via PIX")
public record CreatePayoutRequest(
        @Schema(description = "Valor solicitado em BRL (deve ser maior que 0 e menor ou igual ao saldo disponível)", example = "500.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor do saque é obrigatório")
        @Positive(message = "O valor do saque deve ser maior que zero")
        BigDecimal amount,

        @Schema(description = "Chave PIX de destino", example = "financeiro@studio3d.com.br", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A chave PIX é obrigatória")
        @Size(max = 140, message = "A chave PIX deve ter no máximo 140 caracteres")
        String pixKey,

        @Schema(description = "Tipo da chave PIX", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O tipo da chave PIX é obrigatório")
        PixKeyType pixKeyType
) {
}
