package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.ExtruderType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterConnectivity;
import org.imprime.ai.api.model.enums.PrinterKinematics;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Payload administrativo para edição rápida/parcial de impressora 3D no catálogo")
public record PatchPrinterCatalogRequest(
        @Size(min = 1, max = 100, message = "A marca deve ter entre 1 e 100 caracteres")
        String brand,

        @Size(min = 1, max = 150, message = "O modelo deve ter entre 1 e 150 caracteres")
        String modelName,

        @Positive(message = "O volume de impressão X deve ser positivo")
        BigDecimal buildVolumeXMm,

        @Positive(message = "O volume de impressão Y deve ser positivo")
        BigDecimal buildVolumeYMm,

        @Positive(message = "O volume de impressão Z deve ser positivo")
        BigDecimal buildVolumeZMm,

        Boolean isEnclosed,

        @PositiveOrZero(message = "A temperatura máxima do bico não pode ser negativa")
        Integer maxNozzleTempC,

        @PositiveOrZero(message = "A temperatura máxima da mesa não pode ser negativa")
        Integer maxBedTempC,

        @Positive(message = "O diâmetro padrão do bico deve ser positivo")
        BigDecimal defaultNozzleMm,

        @PositiveOrZero(message = "O diâmetro do filamento não pode ser negativo")
        BigDecimal filamentDiameterMm,

        @Positive(message = "A velocidade máxima de impressão deve ser positiva")
        Integer maxPrintSpeedMmS,

        @PositiveOrZero(message = "A potência máxima 110V não pode ser negativa")
        Integer maxPowerWatts110v,

        @PositiveOrZero(message = "A potência máxima 220V não pode ser negativa")
        Integer maxPowerWatts220v,

        @Size(max = 40, message = "A tensão de entrada deve ter no máximo 40 caracteres")
        String inputVoltage,

        List<PrinterConnectivity> connectivityMethods,

        @Positive(message = "O peso deve ser positivo")
        BigDecimal weightKg,

        List<MaterialType> supportedMaterials,

        PrinterTechnology technology,

        PrinterKinematics kinematics,

        ExtruderType extruderType,

        Boolean supportsMulticolor,

        @Size(max = 80, message = "O nome do sistema multicolor deve ter no máximo 80 caracteres")
        String multicolorSystemName,

        @Min(value = 1, message = "O número máximo de cores/slots deve ser pelo menos 1")
        Integer maxColorSlots,

        @PositiveOrZero(message = "O consumo médio em watts não pode ser negativo")
        Integer avgPowerWatts,

        Boolean activeChamberHeating,

        @PositiveOrZero(message = "A temperatura máxima da câmara não pode ser negativa")
        Integer maxChamberTempC,

        @Positive(message = "A altura mínima de camada deve ser positiva")
        BigDecimal minLayerHeightMm,

        @Positive(message = "A altura máxima de camada deve ser positiva")
        BigDecimal maxLayerHeightMm,

        @Positive(message = "A aceleração máxima deve ser positiva")
        Integer maxAccelerationMmS2,

        List<@Positive(message = "O tamanho do bico deve ser positivo") BigDecimal> supportedNozzleSizesMm,

        Boolean hardenedNozzleDefault,

        Boolean hasAutoBedLeveling,

        Boolean hasFilamentRunoutSensor,

        Boolean hasCamera,

        Boolean hasAirFiltration,

        @Min(value = 2005, message = "Ano de lançamento inválido")
        Integer releaseYear,

        @PositiveOrZero(message = "O preço estimado não pode ser negativo")
        BigDecimal estimatedPriceBrl,

        @Size(max = 1000, message = "A URL da imagem deve ter no máximo 1000 caracteres")
        String imageUrl,

        @Size(max = 500, message = "A URL oficial de especificações deve ter no máximo 500 caracteres")
        String officialSpecsUrl,

        String notes,

        PrinterSpeedTier speedTier,

        BigDecimal speedMultiplier,

        StatusCd status,

        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
        String reason
) {
}
