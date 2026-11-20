package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Projeto multi-peça e controle de produção/BOM do Maker")
public record MakerProjectResponse(
        @Schema(description = "GUID único do projeto")
        String guid,
        @Schema(description = "Nome do projeto", example = "Protótipo Arquitetônico Residencial")
        String name,
        @Schema(description = "Escopo e descrição do projeto")
        String description,
        @Schema(description = "Status atual do projeto", example = "IN_PROGRESS")
        ProjectStatus status,
        @Schema(description = "Nome do cliente", example = "Sofia Castro")
        String clientName,
        @Schema(description = "GUID do pedido vinculado (opcional)")
        String orderGuid,
        @Schema(description = "Código legível do pedido vinculado (opcional)", example = "PED-487")
        String orderCode,
        @Schema(description = "Data limite de entrega (YYYY-MM-DD)", example = "2026-05-22")
        LocalDate dueDate,
        @Schema(description = "Total de mesas/placas", example = "3")
        int totalPlates,
        @Schema(description = "Mesas/placas concluídas", example = "1")
        int completedPlates,
        @Schema(description = "Total de peças", example = "14")
        int totalParts,
        @Schema(description = "Peças concluídas", example = "6")
        int completedParts,
        @Schema(description = "Peso estimado total em gramas", example = "520.0")
        BigDecimal estimatedWeightGrams,
        @Schema(description = "Horas estimadas totais", example = "12.5")
        BigDecimal estimatedHours,
        @Schema(description = "Valor cobrado do cliente em BRL", example = "200.00")
        BigDecimal quotedPrice,
        @Schema(description = "Custo estimado total em BRL", example = "68.40")
        BigDecimal estimatedCost,
        @Schema(description = "Material predominante", example = "PLA")
        MaterialType primaryMaterial,
        @Schema(description = "Cor principal em hexadecimal", example = "#f1f5f9")
        String colorHex,
        @Schema(description = "URL da miniatura do projeto")
        String thumbnailUrl,
        @Schema(description = "GUIDs dos arquivos no Print Archive vinculados ao projeto")
        List<String> fileGuids,
        @Schema(description = "Lista de insumos extras (BOM) do projeto")
        List<BomItemResponse> bomItems,
        @Schema(description = "Observações internas")
        String notes,
        @Schema(description = "Data de criação em UTC (ISO-8601)")
        Instant createdAt
) {
}
