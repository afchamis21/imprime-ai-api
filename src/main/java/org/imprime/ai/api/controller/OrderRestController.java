package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.http.response.order.OrderFileResponse;
import org.imprime.ai.api.http.response.order.OrderShipmentResponse;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.service.OrderManagementService;
import org.imprime.ai.api.service.OrderService;
import org.imprime.ai.api.service.OrderShipmentService;
import org.imprime.ai.api.service.ReviewService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
@JwtAuthenticated
@Tag(name = "Pedidos (REST)", description = "Consulta detalhada de pedidos, transições de status e busca de Makers elegíveis")
public class OrderRestController {
    private final OrderManagementService orderManagementService;
    private final OrderService orderService;
    private final OrderShipmentService orderShipmentService;
    private final ReviewService reviewService;

    @Operation(
            summary = "Obter detalhes de um pedido",
            description = "Retorna o detalhamento completo do pedido, incluindo itens, endereço de entrega e histórico de status. Requer participação como comprador ou Maker."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não possui vínculo com o pedido", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{orderGuid}")
    public ResponseEntity<BaseResponse<OrderDetailResponse>> getOrder(
            @Parameter(description = "GUID do pedido", required = true)
            @PathVariable String orderGuid) {
        return BaseResponse.ok(orderManagementService.getOrderDetail(orderGuid));
    }

    @Operation(summary = "Consultar rastreio do pedido", description = "Retorna transportadora, serviço, status e código de rastreio ao comprador ou Maker participante.")
    @GetMapping("/{orderGuid}/shipment")
    public ResponseEntity<BaseResponse<OrderShipmentResponse>> getOrderShipment(
            @Parameter(description = "GUID do pedido", required = true) @PathVariable String orderGuid) {
        return BaseResponse.ok(orderShipmentService.get(orderGuid));
    }

    @Operation(summary = "Gerar etiqueta do pedido", description = "Cria, compra e solicita a geração do envio selecionado pelo comprador usando a carteira central do Melhor Envio.")
    @PostMapping("/{orderGuid}/shipment")
    public ResponseEntity<BaseResponse<OrderShipmentResponse>> generateOrderShipment(
            @Parameter(description = "GUID do pedido", required = true) @PathVariable String orderGuid) {
        return BaseResponse.ok(orderShipmentService.generate(orderGuid));
    }

    @Operation(summary = "Baixar etiqueta do pedido", description = "Faz o streaming autenticado do PDF da etiqueta para o Maker do pedido.")
    @GetMapping("/{orderGuid}/shipment/{shipmentGuid}/label")
    public ResponseEntity<byte[]> downloadShipmentLabel(
            @PathVariable String orderGuid,
            @PathVariable String shipmentGuid) {
        OrderManagementService.OrderFileContent content = orderShipmentService.downloadLabel(orderGuid, shipmentGuid);
        ContentDisposition disposition = ContentDisposition.builder("attachment")
                .filename(content.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }

    @Operation(
            summary = "Atualizar status de um pedido",
            description = "Executa transição validada da máquina de estados do pedido conforme o perfil do usuário autenticado (comprador ou Maker)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Payload inválido ou transição de status não permitida", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para esta transição", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do pedido", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{orderGuid}/status")
    public ResponseEntity<BaseResponse<OrderDetailResponse>> updateOrderStatus(
            @Parameter(description = "GUID do pedido", required = true)
            @PathVariable String orderGuid,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return BaseResponse.ok(orderManagementService.updateOrderStatus(orderGuid, request));
    }

    @Operation(
            summary = "Avaliar o Maker de um pedido concluído",
            description = "Publica uma avaliação (1 a 5 estrelas e comentário) para o Maker responsável pelo pedido após o recebimento/conclusão (status COMPLETED)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Avaliação publicada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Pedido não elegível ou dados de avaliação inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Apenas o comprador do pedido pode avaliar", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido ou Maker não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Este pedido já foi avaliado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{orderGuid}/review")
    public ResponseEntity<BaseResponse<ReviewResponse>> createOrderMakerReview(
            @Parameter(description = "GUID do pedido", required = true)
            @PathVariable String orderGuid,
            @Valid @RequestBody CreateReviewRequest request) {
        return BaseResponse.created(reviewService.createOrderMakerReview(orderGuid, request));
    }

    @Operation(
            summary = "Buscar Makers próximos para um pedido",
            description = "Retorna a lista de Makers próximos ao endereço de entrega associado ao pedido informado, ordenados por distância."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de Makers retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Pedido ou endereço inválido", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{orderGuid}/makers")
    public ResponseEntity<BaseResponse<List<CompanyChoiceDTO>>> getOrderMakers(
            @Parameter(description = "GUID do pedido", required = true)
            @PathVariable String orderGuid,
            @Parameter(description = "Quantidade máxima de Makers a retornar")
            @RequestParam(value = "max", required = false, defaultValue = "10") Integer max) {
        return BaseResponse.ok(orderService.getMakerOptions(orderGuid, max));
    }

    @Operation(
            summary = "Listar arquivos 3D e mesas de um pedido",
            description = "Retorna a lista ordenada de mesas e arquivos 3D vinculados ao pedido. Requer participação como comprador ou Maker."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arquivos do pedido retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não possui vínculo com o pedido", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{orderGuid}/files")
    public ResponseEntity<BaseResponse<List<OrderFileResponse>>> getOrderFiles(
            @Parameter(description = "GUID do pedido", required = true)
            @PathVariable String orderGuid) {
        return BaseResponse.ok(orderManagementService.listOrderFiles(orderGuid));
    }

    @Operation(
            summary = "Baixar ou visualizar o binário de um arquivo 3D do pedido",
            description = "Faz streaming do conteúdo binário do arquivo 3D associado ao pedido para renderização no visualizador ou download."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteúdo binário do arquivo retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não possui vínculo com o pedido", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido ou arquivo não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{orderGuid}/files/{fileGuid}/content")
    public ResponseEntity<byte[]> getOrderFileContent(
            @Parameter(description = "GUID do pedido", required = true)
            @PathVariable String orderGuid,
            @Parameter(description = "GUID do arquivo no pedido", required = true)
            @PathVariable String fileGuid,
            @Parameter(description = "Se `true`, define `Content-Disposition: attachment` para download direto")
            @RequestParam(defaultValue = "false") boolean download) {
        OrderManagementService.OrderFileContent content = orderManagementService.downloadOrderFile(orderGuid, fileGuid);

        ContentDisposition disposition = ContentDisposition
                .builder(download ? "attachment" : "inline")
                .filename(content.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .body(content.bytes());
    }
}
