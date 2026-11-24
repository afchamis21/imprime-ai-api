package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.imprime.ai.api.quote.QuoteInput;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "Requisição de cálculo em tempo real do motor modular de orçamento 3D")
public record CalculateMakerQuoteRequest(
        @Schema(description = "Quantidade total de peças", example = "2")
        @Min(value = 1, message = "A quantidade deve ser de pelo menos 1")
        Integer quantity,

        @Schema(description = "Escopo das medidas de tempo e peso informadas (per_unit | per_plate | total_batch)", example = "per_unit")
        String inputScope,

        @Schema(description = "Quantidade de peças por mesa cheia (usado quando inputScope = per_plate)", example = "24")
        @Min(value = 1, message = "As peças por mesa devem ser de pelo menos 1")
        Integer unitsPerPlate,

        @Schema(description = "Horas inteiras de impressão", example = "1")
        @DecimalMin(value = "0.0", message = "As horas de impressão não podem ser negativas")
        BigDecimal printTimeHours,

        @Schema(description = "Minutos de impressão", example = "45")
        @DecimalMin(value = "0.0", message = "Os minutos de impressão não podem ser negativos")
        BigDecimal printTimeMinutes,

        @Schema(description = "Seleção ou parâmetros manuais da impressora")
        @Valid
        PrinterSelectionRequest printer,

        @Schema(description = "Lista de filamentos utilizados na peça/lote")
        @Valid
        List<FilamentInputRequest> filaments,

        @Schema(description = "Indica se há mão de obra/acabamento na cotação", example = "true")
        Boolean requiresLabor,

        @Schema(description = "Tempo de mão de obra/acabamento em minutos", example = "15")
        @DecimalMin(value = "0.0", message = "O tempo de mão de obra não pode ser negativo")
        BigDecimal laborTimeMinutes,

        @Schema(description = "Observações sobre acabamento/mão de obra")
        String laborNotes,

        @Schema(description = "Custos extras pontuais do pedido em BRL", example = "4.00")
        @DecimalMin(value = "0.0", message = "Os custos extras não podem ser negativos")
        BigDecimal extraCosts,

        @Schema(description = "Descrição dos custos extras")
        String extraCostsDescription,

        @Schema(description = "Escopo de cobrança dos custos extras (per_order | per_unit)", example = "per_order")
        String extraCostsScope,

        @Schema(description = "Overrides pontuais por código de módulo")
        Map<String, @Valid QuoteModuleOverrideRequest> moduleOverrides
) {
    @Schema(description = "Parâmetros de seleção de impressora")
    public record PrinterSelectionRequest(
            @Schema(description = "Modo de entrada (select | manual)", example = "select")
            String mode,

            @Schema(description = "GUID ou ID da impressora cadastrada", example = "printer-guid-01")
            String printerId,

            @Schema(description = "Potência manual em Watts", example = "300")
            @DecimalMin(value = "0.0", message = "A potência manual não pode ser negativa")
            BigDecimal manualPowerWatts,

            @Schema(description = "Taxa manual de depreciação em BRL/hora")
            @DecimalMin(value = "0.0", message = "A depreciação manual não pode ser negativa")
            BigDecimal manualHourlyDepreciationRate
    ) {
    }

    @Schema(description = "Entrada individual de filamento consumido")
    public record FilamentInputRequest(
            @Schema(description = "Identificador da linha na UI", example = "fil-1")
            String entryId,

            @Schema(description = "Modo de entrada (select | manual)", example = "select")
            String mode,

            @Schema(description = "GUID ou ID do carretel em estoque")
            String filamentId,

            @Schema(description = "Rótulo manual do filamento")
            String manualLabel,

            @Schema(description = "Custo manual por kg em BRL", example = "120.00")
            @DecimalMin(value = "0.0", message = "O custo por kg não pode ser negativo")
            BigDecimal costPerKg,

            @Schema(description = "Peso consumido em gramas", example = "60")
            @DecimalMin(value = "0.0", message = "O peso em gramas não pode ser negativo")
            BigDecimal weightGrams
    ) {
    }

    public QuoteInput toQuoteInput() {
        QuoteInput.PrinterInput mappedPrinter = printer != null
                ? new QuoteInput.PrinterInput(
                        printer.mode(),
                        printer.printerId(),
                        printer.manualPowerWatts(),
                        printer.manualHourlyDepreciationRate())
                : null;

        List<QuoteInput.FilamentInput> mappedFilaments = filaments != null
                ? filaments.stream()
                        .map(item -> item != null
                                ? new QuoteInput.FilamentInput(
                                        item.entryId(),
                                        item.mode(),
                                        item.filamentId(),
                                        item.manualLabel(),
                                        item.costPerKg(),
                                        item.weightGrams())
                                : null)
                        .toList()
                : List.of();

        Map<String, QuoteInput.ModuleOverrideInput> mappedOverrides = Map.of();
        if (moduleOverrides != null && !moduleOverrides.isEmpty()) {
            Map<String, QuoteInput.ModuleOverrideInput> buffer = new LinkedHashMap<>();
            for (Map.Entry<String, QuoteModuleOverrideRequest> entry : moduleOverrides.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    buffer.put(entry.getKey(), new QuoteInput.ModuleOverrideInput(
                            entry.getValue().enabled(),
                            entry.getValue().config()
                    ));
                }
            }
            mappedOverrides = Map.copyOf(buffer);
        }

        return new QuoteInput(
                quantity,
                inputScope,
                unitsPerPlate,
                printTimeHours,
                printTimeMinutes,
                mappedPrinter,
                mappedFilaments,
                requiresLabor,
                laborTimeMinutes,
                laborNotes,
                extraCosts,
                extraCostsDescription,
                extraCostsScope,
                mappedOverrides
        );
    }
}
