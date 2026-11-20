package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Configurações operacionais e de automação da impressora")
public record PrinterConfigRequest(
        @Schema(description = "Aceita pedidos compatíveis automaticamente", example = "true")
        Boolean autoAcceptOrders,

        @Schema(description = "Habilita integração via chave de API externa", example = "true")
        Boolean apiEnabled,

        @Schema(description = "Altura máxima de camada configurada em mm", example = "0.20")
        @Positive(message = "A altura de camada deve ser positiva")
        BigDecimal maxLayerHeightMm,

        @Schema(description = "Preenchimento padrão em percentual (0 a 100)", example = "20")
        @Min(value = 0, message = "O preenchimento mínimo é 0%")
        @Max(value = 100, message = "O preenchimento máximo é 100%")
        Integer defaultInfillPercent,

        @Schema(description = "Duração máxima aceita por impressão em horas", example = "36")
        @Min(value = 1, message = "O tempo máximo deve ser de pelo menos 1 hora")
        Integer maxPrintTimeHours,

        @Schema(description = "GUIDs dos filamentos vinculados a esta impressora")
        List<String> linkedFilamentGuids,

        @Schema(description = "Notas operacionais e de calibração")
        @Size(max = 1000, message = "As notas devem ter no máximo 1000 caracteres")
        String notes
) {
}
