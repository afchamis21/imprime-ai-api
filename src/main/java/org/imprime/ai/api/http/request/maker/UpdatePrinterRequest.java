package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
        PrinterConfigRequest config
) {
}
