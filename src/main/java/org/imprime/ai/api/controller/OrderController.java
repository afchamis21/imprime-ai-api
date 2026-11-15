package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order")
@Tag(name = "Pedidos", description = "Endpoints para consulta de pedidos e opções de Makers disponíveis")
public class OrderController {
    private final OrderService orderService;

    @Operation(
            summary = "Buscar Makers disponíveis para um pedido",
            description = "Retorna a lista de empresas (Makers) próximas ao endereço de entrega associado ao pedido informado, ordenadas por proximidade."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de Makers retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Pedido ou endereço de entrega não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/makers")
    public ResponseEntity<BaseResponse<List<CompanyChoiceDTO>>> getMakers(
            @Parameter(description = "GUID do pedido para o qual se deseja buscar Makers", required = true)
            @RequestParam String orderGuid,
            @Parameter(description = "Quantidade máxima de Makers a retornar")
            @RequestParam(value = "max", required = false, defaultValue = "10") Integer max) {
        return BaseResponse.build(orderService.getMakerOptions(orderGuid, max), HttpStatus.OK);
    }
}
