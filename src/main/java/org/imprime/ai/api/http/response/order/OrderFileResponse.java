package org.imprime.ai.api.http.response.order;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Arquivo 3D e metadados de mesa associados a um pedido")
public record OrderFileResponse(
        @Schema(description = "GUID do arquivo (`FileAsset`) no pedido", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String fileGuid,
        @Schema(description = "Nome do arquivo 3D", example = "suporte_placa.stl")
        String fileName,
        @Schema(description = "Número sequencial da mesa", example = "1")
        Integer plateNumber,
        @Schema(description = "Nome exibido da mesa", example = "Mesa 1 (suporte)")
        String plateName,
        @Schema(description = "Material selecionado na mesa", example = "PLA")
        String material,
        @Schema(description = "Cor selecionada em hexadecimal", example = "#8b5cf6")
        String colorHex,
        @Schema(description = "Percentual de preenchimento (infill)", example = "20")
        Integer infillPercent,
        @Schema(description = "Indica se possui suporte ativado", example = "true")
        Boolean supports,
        @Schema(description = "Escala no eixo X", example = "1.0")
        Double scaleX,
        @Schema(description = "Escala no eixo Y", example = "1.0")
        Double scaleY,
        @Schema(description = "Escala no eixo Z", example = "1.0")
        Double scaleZ,
        @Schema(description = "Indica se é impressão multicor", example = "false")
        Boolean isMultiColor,
        @Schema(description = "Instruções de pintura multicor")
        String paintingInstructions
) {
}
