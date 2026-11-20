package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.PixKeyType;

import java.math.BigDecimal;

@Schema(description = "Tarifas operacionais e chave PIX vigentes no estúdio")
public record FinanceRatesResponse(
        @Schema(description = "Tarifa de energia elétrica em BRL/kWh", example = "0.98")
        BigDecimal kwhRate,
        @Schema(description = "Taxa horária de depreciação em BRL/hora", example = "2.10")
        BigDecimal hourlyDepreciationRate,
        @Schema(description = "Chave PIX padrão mascarada", example = "fin***@studio3d.com.br")
        String pixKeyMasked,
        @Schema(description = "Tipo da chave PIX padrão", example = "EMAIL")
        PixKeyType pixKeyType
) {
}
