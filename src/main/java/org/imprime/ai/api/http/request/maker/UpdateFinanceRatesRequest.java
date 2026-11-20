package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.PixKeyType;

import java.math.BigDecimal;

@Schema(description = "Atualização das tarifas operacionais e chave PIX padrão do Maker")
public record UpdateFinanceRatesRequest(
        @Schema(description = "Tarifa de energia elétrica em BRL/kWh", example = "0.98")
        @Positive(message = "A tarifa de kWh deve ser positiva")
        BigDecimal kwhRate,

        @Schema(description = "Taxa horária de depreciação por impressora em BRL/h", example = "2.10")
        @PositiveOrZero(message = "A taxa de depreciação não pode ser negativa")
        BigDecimal hourlyDepreciationRate,

        @Schema(description = "Chave PIX padrão", example = "financeiro@studio3d.com.br")
        @Size(max = 140, message = "A chave PIX deve ter no máximo 140 caracteres")
        String pixKey,

        @Schema(description = "Tipo da chave PIX padrão", example = "EMAIL")
        PixKeyType pixKeyType
) {
}
