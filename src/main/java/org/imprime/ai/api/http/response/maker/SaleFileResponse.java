package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.OrderStatusCd;

import java.time.Instant;

@Schema(description = "Arquivo 3D vinculado a uma venda fechada do Maker")
public record SaleFileResponse(
        @Schema(description = "GUID do arquivo (FileAsset) — usar em /orders/{orderGuid}/files/{fileGuid}/content")
        String fileGuid,
        @Schema(description = "Nome técnico do arquivo", example = "capacete_mesa1.stl")
        String fileName,
        @Schema(description = "Nome amigável da mesa/peça", example = "Mesa 1 — Capacete")
        String displayName,
        @Schema(description = "Número da mesa/placa dentro do pedido", example = "1")
        int plateNumber,
        @Schema(description = "Extensão do arquivo em maiúsculas", example = "STL")
        String format,
        @Schema(description = "Material solicitado (quando informado)", example = "PLA")
        String material,
        @Schema(description = "Cor solicitada (quando informada)", example = "#f1f5f9")
        String colorHex,
        @Schema(description = "Origem do arquivo: ORDER_PLATE, ORDER_FILE ou MODEL_3D")
        String sourceType,
        @Schema(description = "GUID do pedido")
        String orderGuid,
        @Schema(description = "Código legível do pedido", example = "PED-487")
        String orderCode,
        @Schema(description = "Status atual do pedido", example = "IN_PRODUCTION")
        OrderStatusCd orderStatus,
        @Schema(description = "Nome do cliente")
        String customerName,
        @Schema(description = "Data da venda em UTC (ISO-8601)")
        Instant saleDate,
        @Schema(description = "Prazo de entrega em UTC (ISO-8601)")
        Instant dueAt,
        @Schema(description = "Quantidade total de arquivos do pedido", example = "3")
        int orderFilesCount
) {
}
