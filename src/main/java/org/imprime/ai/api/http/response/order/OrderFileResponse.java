package org.imprime.ai.api.http.response.order;

import io.swagger.v3.oas.annotations.media.Schema;

import org.imprime.ai.api.model.enums.SlicingValidationStatus;

import java.math.BigDecimal;
import java.util.List;

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
        String paintingInstructions,
        @Schema(description = "Dimensão escalada X em mm", example = "120.5")
        BigDecimal dimensionXMm,
        @Schema(description = "Dimensão escalada Y em mm", example = "80.0")
        BigDecimal dimensionYMm,
        @Schema(description = "Dimensão escalada Z em mm", example = "45.0")
        BigDecimal dimensionZMm,
        @Schema(description = "Quantidade de cores", example = "1")
        Integer colorCount,
        @Schema(description = "Altura de camada em mm", example = "0.20")
        BigDecimal layerHeightMm,
        @Schema(description = "Urgência da entrega (standard, express)", example = "standard")
        String urgency,
        @Schema(description = "Quantidade de peças nesta mesa", example = "2")
        Integer quantity,
        @Schema(description = "Quantidade de paredes/perímetros", example = "3")
        Integer wallCount,
        @Schema(description = "Padrão de preenchimento", example = "gyroid")
        String infillPattern,
        @Schema(description = "Tipo de adesão na mesa", example = "brim")
        String adhesionType,
        @Schema(description = "Tipo de suporte selecionado", example = "tree")
        String supportType,
        @Schema(description = "Acabamento solicitado", example = "sanded")
        String finish,
        @Schema(description = "Tolerância dimensional", example = "tight")
        String tolerance,
        @Schema(description = "Observações informadas pelo usuário para a mesa")
        String notes,
        @Schema(description = "Lista de cores selecionadas em hexadecimal")
        List<String> colors,
        @Schema(description = "Peso estimado pelo fatiador em gramas", example = "48.5")
        BigDecimal estimatedWeightGrams,
        @Schema(description = "Tempo estimado de impressão pelo fatiador em minutos", example = "135")
        Integer estimatedPrintTimeMinutes,
        @Schema(description = "Preço estimado pelo fatiador em BRL", example = "42.90")
        BigDecimal estimatedPriceBrl,
        String slicerEngineId,
        String slicerProfileId,
        BigDecimal clientModelWeightGrams,
        BigDecimal clientSupportWeightGrams,
        Integer clientBasePrintTimeSeconds,
        BigDecimal serverModelWeightGrams,
        BigDecimal serverSupportWeightGrams,
        BigDecimal serverTotalWeightGrams,
        Integer serverBasePrintTimeSeconds,
        SlicingValidationStatus slicingValidationStatus,
        Boolean hasValidated3mf
) {
    public OrderFileResponse(
            String fileGuid,
            String fileName,
            Integer plateNumber,
            String plateName,
            String material,
            String colorHex,
            Integer infillPercent,
            Boolean supports,
            Double scaleX,
            Double scaleY,
            Double scaleZ,
            Boolean isMultiColor,
            String paintingInstructions
    ) {
        this(
                fileGuid, fileName, plateNumber, plateName, material, colorHex,
                infillPercent, supports, scaleX, scaleY, scaleZ, isMultiColor, paintingInstructions,
                null, null, null, null, null, null,
                1, null, null, null, null, null, null, null,
                colorHex != null && !colorHex.isBlank() ? List.of(colorHex) : List.of(),
                null, null, null,
                null, null, null, null, null, null, null, null, null, null, null
        );
    }

    public OrderFileResponse(
            String fileGuid,
            String fileName,
            Integer plateNumber,
            String plateName,
            String material,
            String colorHex,
            Integer infillPercent,
            Boolean supports,
            Double scaleX,
            Double scaleY,
            Double scaleZ,
            Boolean isMultiColor,
            String paintingInstructions,
            BigDecimal dimensionXMm,
            BigDecimal dimensionYMm,
            BigDecimal dimensionZMm,
            Integer colorCount,
            BigDecimal layerHeightMm,
            String urgency,
            Integer quantity,
            Integer wallCount,
            String infillPattern,
            String adhesionType,
            String supportType,
            String finish,
            String tolerance,
            String notes,
            List<String> colors,
            BigDecimal estimatedWeightGrams,
            Integer estimatedPrintTimeMinutes,
            BigDecimal estimatedPriceBrl
    ) {
        this(
                fileGuid, fileName, plateNumber, plateName, material, colorHex,
                infillPercent, supports, scaleX, scaleY, scaleZ, isMultiColor, paintingInstructions,
                dimensionXMm, dimensionYMm, dimensionZMm, colorCount, layerHeightMm, urgency,
                quantity, wallCount, infillPattern, adhesionType, supportType, finish, tolerance, notes,
                colors, estimatedWeightGrams, estimatedPrintTimeMinutes, estimatedPriceBrl,
                null, null, null, null, null, null, null, null, null, null, null
        );
    }
}
