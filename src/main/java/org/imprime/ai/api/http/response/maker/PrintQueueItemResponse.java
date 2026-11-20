package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.QueuePriority;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Item da fila de impressão do Maker")
public record PrintQueueItemResponse(
        @Schema(description = "GUID único do job na fila")
        String guid,
        @Schema(description = "Posição ordenada na fila", example = "1")
        int position,
        @Schema(description = "Título do job", example = "Protótipo Arquitetônico — Mesa 2/3")
        String title,
        @Schema(description = "GUID do arquivo no Print Archive (opcional)")
        String fileGuid,
        @Schema(description = "Nome do arquivo", example = "prototipo_arquitetonico_torre_a.3mf")
        String fileName,
        @Schema(description = "URL da miniatura")
        String thumbnailUrl,
        @Schema(description = "GUID da impressora designada")
        String printerGuid,
        @Schema(description = "Nome da impressora designada", example = "X1C Principal")
        String printerName,
        @Schema(description = "GUID do filamento selecionado")
        String filamentGuid,
        @Schema(description = "Material utilizado", example = "PLA")
        MaterialType material,
        @Schema(description = "Nome da cor", example = "Branco")
        String colorName,
        @Schema(description = "Código hexadecimal da cor", example = "#f1f5f9")
        String colorHex,
        @Schema(description = "Peso estimado em gramas", example = "185.0")
        BigDecimal estimatedWeightGrams,
        @Schema(description = "Tempo estimado em minutos", example = "260")
        int estimatedTimeMinutes,
        @Schema(description = "Progresso percentual (0 a 100)", example = "68")
        int progressPercent,
        @Schema(description = "Status atual do job", example = "PRINTING")
        PrintJobStatus status,
        @Schema(description = "Prioridade na fila", example = "URGENT")
        QueuePriority priority,
        @Schema(description = "Total de cópias solicitadas", example = "1")
        int copies,
        @Schema(description = "Cópias já concluídas", example = "0")
        int completedCopies,
        @Schema(description = "GUID do pedido vinculado (opcional)")
        String orderGuid,
        @Schema(description = "Código legível do pedido vinculado (opcional)", example = "PED-487")
        String orderCode,
        @Schema(description = "GUID do projeto multi-peça vinculado (opcional)")
        String projectGuid,
        @Schema(description = "Nome do cliente associado")
        String customerName,
        @Schema(description = "Motivo de falha (quando status = FAILED)")
        String failureReason,
        @Schema(description = "Data/hora agendada de início em UTC (ISO-8601)")
        Instant scheduledStartAt,
        @Schema(description = "Data de criação em UTC (ISO-8601)")
        Instant createdAt
) {
}
