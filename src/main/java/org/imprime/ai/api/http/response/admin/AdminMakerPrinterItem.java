package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.PrinterStatus;

import java.math.BigDecimal;

@Schema(description = "Resumo de impressora 3D cadastrada no estúdio do Maker")
public record AdminMakerPrinterItem(
        String guid,
        String name,
        String make,
        String model,
        Short manufactureYear,
        BigDecimal trayWidth,
        BigDecimal trayDepth,
        BigDecimal trayHeight,
        BigDecimal nozzleDiameterMm,
        PrinterStatus printerStatus
) {
}
