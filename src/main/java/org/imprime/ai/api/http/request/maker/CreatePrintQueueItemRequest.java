package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.QueuePriority;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Dados para enfileiramento manual de um novo job de impressão")
public record CreatePrintQueueItemRequest(
        @Schema(description = "Título identificador do job na fila", example = "Suporte de Headset Minimalista", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O título do job é obrigatório")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres")
        String title,

        @Schema(description = "GUID da impressora designada", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A impressora é obrigatória")
        String printerGuid,

        @Schema(description = "GUID do arquivo no Print Archive (opcional — preenche defaults automaticamente)")
        String fileGuid,

        @Schema(description = "Nome do arquivo caso não venha do Print Archive")
        @Size(max = 255, message = "O nome do arquivo deve ter no máximo 255 caracteres")
        String fileName,

        @Schema(description = "GUID do rolo de filamento/resina")
        String filamentGuid,

        @Schema(description = "Override do material previsto")
        MaterialType material,

        @Schema(description = "Override do nome da cor")
        @Size(max = 60, message = "O nome da cor deve ter no máximo 60 caracteres")
        String colorName,

        @Schema(description = "Override do código hexadecimal #RRGGBB da cor")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor hexadecimal inválida")
        String colorHex,

        @Schema(description = "Peso estimado em gramas", example = "118.0")
        @Positive(message = "O peso estimado deve ser positivo")
        BigDecimal estimatedWeightGrams,

        @Schema(description = "Duração estimada em minutos", example = "140")
        @Min(value = 1, message = "A duração mínima é 1 minuto")
        Integer estimatedTimeMinutes,

        @Schema(description = "Prioridade na fila (padrão NORMAL)", example = "NORMAL")
        QueuePriority priority,

        @Schema(description = "Quantidade de cópias (padrão 1)", example = "2")
        @Min(value = 1, message = "A quantidade mínima de cópias é 1")
        Integer copies,

        @Schema(description = "GUID do pedido vinculado (opcional)")
        String orderGuid,

        @Schema(description = "GUID do projeto multi-peça vinculado (opcional)")
        String projectGuid,

        @Schema(description = "Nome do cliente (opcional, caso sem pedido vinculado)")
        @Size(max = 120, message = "O nome do cliente deve ter no máximo 120 caracteres")
        String customerName,

        @Schema(description = "Início agendado em UTC (ISO-8601) para posicionamento na timeline")
        Instant scheduledStartAt
) {
}
