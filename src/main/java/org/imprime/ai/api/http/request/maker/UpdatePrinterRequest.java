package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterStatus;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados para atualização parcial (PATCH) de uma impressora 3D")
public record UpdatePrinterRequest(
        @Schema(description = "Nome operacional atualizado")
        @Size(min = 1, max = 100, message = "O nome deve ter entre 1 e 100 caracteres")
        String name,

        @Schema(description = "Modelo/fabricante atualizado")
        @Size(min = 1, max = 120, message = "O modelo deve ter entre 1 e 120 caracteres")
        String model,

        @Schema(description = "Estado operacional da impressora")
        PrinterStatus status,

        @Schema(description = "Volume útil atualizado")
        @Valid
        BuildVolumeRequest buildVolume,

        @Schema(description = "Diâmetro do bico em mm")
        @Positive(message = "O diâmetro do bico deve ser positivo")
        BigDecimal nozzleDiameterMm,

        @Schema(description = "Consumo elétrico em Watts")
        @Positive(message = "A potência deve ser positiva")
        Integer powerWatts,

        @Schema(description = "Endereço IP na LAN")
        @Size(max = 60, message = "O endereço IP deve ter no máximo 60 caracteres")
        String ipAddress,

        @Schema(description = "Mapeamento de slots AMS")
        List<@Valid AmsSlotRequest> amsSlots,

        @Schema(description = "Configurações operacionais da impressora")
        @Valid
        PrinterConfigRequest config,

        @Schema(description = "Valor de aquisição da impressora em BRL (opcional)", example = "4200.00")
        @PositiveOrZero(message = "O valor de aquisição não pode ser negativo")
        BigDecimal purchasePrice,

        @Schema(description = "Vida útil estimada da impressora em horas (opcional)", example = "3000")
        @Positive(message = "A vida útil em horas deve ser positiva")
        Integer lifespanHours,

        @Schema(description = "Taxa horária específica de depreciação/manutenção em BRL/h (opcional)", example = "1.90")
        @PositiveOrZero(message = "A taxa horária de depreciação não pode ser negativa")
        BigDecimal hourlyDepreciationRate,

        @Schema(description = "Quando true, limpa explicitamente o valor de aquisição salvo")
        Boolean clearPurchasePrice,

        @Schema(description = "Quando true, limpa explicitamente a vida útil em horas salva")
        Boolean clearLifespanHours,

        @Schema(description = "Quando true, limpa explicitamente a taxa horária de depreciação salva")
        Boolean clearHourlyDepreciationRate,

        @Schema(description = "GUID da impressora no catálogo fixo (`printer_catalog`)")
        String printerCatalogGuid,

        @Schema(description = "Indica se esta impressora imprime colorido / multicolor")
        Boolean supportsMulticolor,

        @Schema(description = "Número de cores/slots simultâneos desta impressora")
        @Min(value = 1, message = "O número de slots deve ser pelo menos 1")
        Integer multicolorSlots,

        @Schema(description = "Sobrescrita opcional indicando se a unidade é fechada")
        Boolean isEnclosedOverride,

        @Schema(description = "Sobrescrita opcional dos materiais suportados por esta unidade")
        List<MaterialType> supportedMaterialsOverride,

        @Schema(description = "Categoria de velocidade opcional da impressora")
        PrinterSpeedTier speedTier,

        @DecimalMin("0.50")
        @DecimalMax("5.00")
        @Schema(description = "Multiplicador de velocidade customizado opcional")
        BigDecimal speedMultiplierOverride
) {
    public UpdatePrinterRequest(
            String name,
            String model,
            PrinterStatus status,
            BuildVolumeRequest buildVolume,
            BigDecimal nozzleDiameterMm,
            Integer powerWatts,
            String ipAddress,
            List<AmsSlotRequest> amsSlots,
            PrinterConfigRequest config,
            BigDecimal purchasePrice,
            Integer lifespanHours,
            BigDecimal hourlyDepreciationRate,
            Boolean clearPurchasePrice,
            Boolean clearLifespanHours,
            Boolean clearHourlyDepreciationRate
    ) {
        this(
                name, model, status, buildVolume, nozzleDiameterMm, powerWatts, ipAddress,
                amsSlots, config, purchasePrice, lifespanHours, hourlyDepreciationRate,
                clearPurchasePrice, clearLifespanHours, clearHourlyDepreciationRate,
                null, null, null, null, null, null, null
        );
    }

    public UpdatePrinterRequest(
            String name,
            String model,
            PrinterStatus status,
            BuildVolumeRequest buildVolume,
            BigDecimal nozzleDiameterMm,
            Integer powerWatts,
            String ipAddress,
            List<AmsSlotRequest> amsSlots,
            PrinterConfigRequest config,
            BigDecimal purchasePrice,
            Integer lifespanHours,
            BigDecimal hourlyDepreciationRate,
            Boolean clearPurchasePrice,
            Boolean clearLifespanHours,
            Boolean clearHourlyDepreciationRate,
            String printerCatalogGuid,
            Boolean supportsMulticolor,
            Integer multicolorSlots,
            Boolean isEnclosedOverride,
            List<MaterialType> supportedMaterialsOverride
    ) {
        this(
                name, model, status, buildVolume, nozzleDiameterMm, powerWatts, ipAddress,
                amsSlots, config, purchasePrice, lifespanHours, hourlyDepreciationRate,
                clearPurchasePrice, clearLifespanHours, clearHourlyDepreciationRate,
                printerCatalogGuid, supportsMulticolor, multicolorSlots, isEnclosedOverride, supportedMaterialsOverride,
                null, null
        );
    }
}
