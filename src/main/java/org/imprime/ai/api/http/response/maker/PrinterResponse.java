package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.catalog.PrinterCatalogBriefDto;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterStatus;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados completos de uma impressora 3D da frota do Maker")
public record PrinterResponse(
        @Schema(description = "GUID único da impressora")
        String guid,
        @Schema(description = "Nome operacional/apelido", example = "X1C Principal")
        String name,
        @Schema(description = "Modelo/fabricante", example = "Bambu Lab X1 Carbon")
        String model,
        @Schema(description = "Estado operacional atual", example = "PRINTING")
        PrinterStatus status,
        @Schema(description = "Volume útil da impressora")
        BuildVolumeDto buildVolume,
        @Schema(description = "Diâmetro do bico em mm", example = "0.4")
        BigDecimal nozzleDiameterMm,
        @Schema(description = "Consumo elétrico em Watts", example = "350")
        Integer powerWatts,
        @Schema(description = "Endereço IP local", example = "192.168.1.42")
        String ipAddress,
        @Schema(description = "Número de série", example = "00M09A3812014")
        String serialNumber,
        @Schema(description = "Slots AMS vinculados")
        List<AmsSlotDto> amsSlots,
        @Schema(description = "Telemetria em tempo real")
        PrinterTelemetryDto telemetry,
        @Schema(description = "Configurações operacionais")
        PrinterConfigDto config,
        @Schema(description = "Valor de aquisição da impressora em BRL (opcional)", example = "4200.00")
        BigDecimal purchasePrice,
        @Schema(description = "Vida útil estimada da impressora em horas (opcional)", example = "3000")
        Integer lifespanHours,
        @Schema(description = "Taxa horária específica de depreciação/manutenção em BRL/h (opcional)", example = "1.90")
        BigDecimal hourlyDepreciationRate,
        @Schema(description = "GUID da impressora no catálogo fixo")
        String printerCatalogGuid,
        @Schema(description = "Dados resumidos do modelo de catálogo vinculado")
        PrinterCatalogBriefDto catalogPrinter,
        @Schema(description = "Indica se esta impressora suporta impressão multicolor")
        boolean supportsMulticolor,
        @Schema(description = "Número máximo de cores/slots simultâneos")
        int multicolorSlots,
        @Schema(description = "Sobrescrita opcional de câmara fechada")
        Boolean isEnclosedOverride,
        @Schema(description = "Sobrescrita opcional de materiais suportados")
        List<MaterialType> supportedMaterialsOverride,
        @Schema(description = "Lista efetiva de materiais suportados por esta impressora")
        List<MaterialType> effectiveSupportedMaterials,
        @Schema(description = "Indica se a unidade efetivamente opera em câmara fechada")
        boolean effectiveEnclosed
) {
    public PrinterResponse(
            String guid,
            String name,
            String model,
            PrinterStatus status,
            BuildVolumeDto buildVolume,
            BigDecimal nozzleDiameterMm,
            Integer powerWatts,
            String ipAddress,
            String serialNumber,
            List<AmsSlotDto> amsSlots,
            PrinterTelemetryDto telemetry,
            PrinterConfigDto config,
            BigDecimal purchasePrice,
            Integer lifespanHours,
            BigDecimal hourlyDepreciationRate
    ) {
        this(
                guid, name, model, status, buildVolume, nozzleDiameterMm, powerWatts, ipAddress,
                serialNumber, amsSlots, telemetry, config, purchasePrice, lifespanHours, hourlyDepreciationRate,
                null, null, false, 1, null, List.of(), List.of(), false
        );
    }
}
