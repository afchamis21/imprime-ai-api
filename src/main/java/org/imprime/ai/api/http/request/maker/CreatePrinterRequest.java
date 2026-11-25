package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados para cadastro de nova impressora 3D na frota do Maker")
public record CreatePrinterRequest(
        @Schema(description = "Nome operacional/apelido da impressora", example = "Bambu Lab P1S #2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome da impressora é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @Schema(description = "Modelo/fabricante da impressora (opcional quando `printerCatalogGuid` é informado)", example = "Bambu Lab P1S Combo")
        @Size(max = 120, message = "O modelo deve ter no máximo 120 caracteres")
        String model,

        @Schema(description = "Volume de impressão em mm (padrão 256x256x256 ou preenchido pelo catálogo)")
        @Valid
        BuildVolumeRequest buildVolume,

        @Schema(description = "Diâmetro do bico em mm (padrão 0.4)", example = "0.4")
        @Positive(message = "O diâmetro do bico deve ser positivo")
        BigDecimal nozzleDiameterMm,

        @Schema(description = "Potência elétrica estimada em Watts (padrão 300)", example = "300")
        @Positive(message = "A potência deve ser positiva")
        Integer powerWatts,

        @Schema(description = "Endereço IP na rede local do estúdio", example = "192.168.1.90")
        @Size(max = 60, message = "O endereço IP deve ter no máximo 60 caracteres")
        String ipAddress,

        @Schema(description = "Valor de aquisição da impressora em BRL (opcional)", example = "4200.00")
        @PositiveOrZero(message = "O valor de aquisição não pode ser negativo")
        BigDecimal purchasePrice,

        @Schema(description = "Vida útil estimada da impressora em horas (opcional)", example = "3000")
        @Positive(message = "A vida útil em horas deve ser positiva")
        Integer lifespanHours,

        @Schema(description = "Taxa horária específica de depreciação/manutenção em BRL/h (opcional)", example = "1.90")
        @PositiveOrZero(message = "A taxa horária de depreciação não pode ser negativa")
        BigDecimal hourlyDepreciationRate,

        @Schema(description = "GUID da impressora no catálogo fixo (`printer_catalog`)")
        String printerCatalogGuid,

        @Schema(description = "Indica se esta impressora imprime colorido / multicolor", example = "true")
        Boolean supportsMulticolor,

        @Schema(description = "Número de cores/slots simultâneos desta impressora", example = "4")
        @Min(value = 1, message = "O número de slots deve ser pelo menos 1")
        Integer multicolorSlots,

        @Schema(description = "Sobrescrita opcional indicando se a unidade é fechada")
        Boolean isEnclosedOverride,

        @Schema(description = "Sobrescrita opcional dos materiais suportados por esta unidade")
        List<MaterialType> supportedMaterialsOverride
) {
    public CreatePrinterRequest(
            String name,
            String model,
            BuildVolumeRequest buildVolume,
            BigDecimal nozzleDiameterMm,
            Integer powerWatts,
            String ipAddress,
            BigDecimal purchasePrice,
            Integer lifespanHours,
            BigDecimal hourlyDepreciationRate
    ) {
        this(
                name, model, buildVolume, nozzleDiameterMm, powerWatts, ipAddress,
                purchasePrice, lifespanHours, hourlyDepreciationRate,
                null, null, null, null, null
        );
    }
}
