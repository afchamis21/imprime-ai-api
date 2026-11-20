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
import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.service.OrderManagementService;
import org.imprime.ai.api.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
@JwtAuthenticated
@Tag(name = "Pedidos (REST)", description = "Consulta detalhada de pedidos, transições de status e busca de Makers elegíveis")
public class OrderRestController {
    private final OrderManagementService orderManagementService;
    private final OrderService orderService;

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
}
