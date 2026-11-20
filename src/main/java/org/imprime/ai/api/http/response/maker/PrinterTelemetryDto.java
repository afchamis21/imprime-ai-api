package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Telemetria em tempo real de uma impressora 3D")
public record PrinterTelemetryDto(
        @Schema(description = "Temperatura atual do bico (°C)", example = "220.0")
        BigDecimal nozzleTempC,
        @Schema(description = "Temperatura alvo do bico (°C)", example = "220.0")
        BigDecimal nozzleTargetC,
        @Schema(description = "Temperatura atual da mesa (°C)", example = "60.0")
        BigDecimal bedTempC,
        @Schema(description = "Temperatura alvo da mesa (°C)", example = "60.0")
        BigDecimal bedTargetC,
        @Schema(description = "Temperatura da câmara (°C)", example = "36.0")
        BigDecimal chamberTempC,
        @Schema(description = "Velocidade da ventoinha (%)", example = "70")
        Integer fanSpeedPercent,
        @Schema(description = "GUID do job atualmente em execução (nulo se ociosa)")
        String currentJobGuid,
        @Schema(description = "Nome do job atualmente em execução")
        String currentJobName,
        @Schema(description = "Progresso percentual da impressão (0 a 100)", example = "68")
        Integer progressPercent,
        @Schema(description = "Tempo restante estimado em minutos", example = "84")
        Integer etaMinutes,
        @Schema(description = "Camada atual", example = "184")
        Integer currentLayer,
        @Schema(description = "Total de camadas", example = "270")
        Integer totalLayers,
        @Schema(description = "Total acumulado de horas de impressão", example = "842.5")
        BigDecimal totalPrintHours
) {
}
