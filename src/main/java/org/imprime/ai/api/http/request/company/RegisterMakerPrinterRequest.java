package org.imprime.ai.api.http.request.company;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Impressora 3D selecionada do catálogo pelo Maker durante o cadastro")
public record RegisterMakerPrinterRequest(
        @Schema(description = "GUID da impressora no catálogo fixo (`printer_catalog`)", requiredMode = Schema.RequiredMode.REQUIRED)
        String printerCatalogGuid,

        @Schema(description = "Apelido/nome operacional opcional na oficina do Maker", example = "Bambu A1 #1")
        String nickname,

        @Schema(description = "Quantidade de unidades desse modelo (padrão 1, máx. 20)", example = "1")
        Integer quantity,

        @Schema(description = "Indica se esta unidade possui módulo multicolor/AMS instalado", example = "true")
        Boolean supportsMulticolor,

        @Schema(description = "Quantidade de slots/cores nessa unidade", example = "4")
        Integer multicolorSlots,

        @Schema(description = "Diâmetro do bico instalado em mm (opcional; usa o padrão do catálogo)", example = "0.4")
        BigDecimal nozzleDiameterMm,

        @Schema(description = "Sobrescrita opcional indicando se a unidade opera fechada")
        Boolean isEnclosedOverride
) {
}
