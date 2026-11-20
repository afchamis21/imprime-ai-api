package org.imprime.ai.api.controller.maker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireMaker;
import org.imprime.ai.api.http.request.maker.PatchMakerProfileRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProfileRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.maker.MakerDashboardResponse;
import org.imprime.ai.api.http.response.maker.MakerStudioProfileResponse;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.service.MakerStudioService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker")
@RequireMaker
@Tag(name = "Maker — Dashboard, Pedidos e Perfil", description = "Visão executiva, histórico de pedidos recebidos e perfil operacional do estúdio Maker")
public class MakerStudioController {
    private final MakerStudioService makerStudioService;

    @Operation(
            summary = "Obter dashboard executivo do Maker",
            description = "Retorna KPIs financeiros/operacionais, chats ativos, pedidos recentes e resumo em tempo real da frota de impressoras."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dashboard retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/dashboard")
    public ResponseEntity<BaseResponse<MakerDashboardResponse>> getDashboard() {
        return BaseResponse.ok(makerStudioService.getDashboard());
    }

    @Operation(
            summary = "Listar pedidos recebidos pelo Maker",
            description = "Retorna página com o histórico de vendas e pedidos sob demanda atribuídos ao Maker autenticado, com filtros por status, busca e período."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedidos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/orders")
    public ResponseEntity<BaseResponse<PageResponse<OrderSummaryResponse>>> listOrders(
            @Parameter(description = "Filtrar por status do pedido")
            @RequestParam(required = false) OrderStatusCd status,
            @Parameter(description = "Busca por código do pedido ou nome do comprador")
            @RequestParam(required = false) String q,
            @Parameter(description = "Data inicial (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Data final (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerStudioService.listMakerOrders(status, q, fromDate, toDate, pageable));
    }

    @Operation(
            summary = "Obter configurações do perfil operacional do Maker",
            description = "Retorna as configurações atuais de perfil público, capacidades, prazos, frete, modo férias e chave PIX mascarada do estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Perfil operacional retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/profile")
    public ResponseEntity<BaseResponse<MakerStudioProfileResponse>> getProfile() {
        return BaseResponse.ok(makerStudioService.getStudioProfile());
    }

    @Operation(
            summary = "Atualizar integralmente perfil do estúdio Maker (PUT)",
            description = "Atualiza todos os parâmetros do perfil operacional e público do estúdio vinculado ao Maker autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/profile")
    public ResponseEntity<BaseResponse<MakerStudioProfileResponse>> replaceProfile(
            @Valid @RequestBody UpdateMakerProfileRequest request) {
        return BaseResponse.ok(makerStudioService.replaceStudioProfile(request));
    }

    @Operation(
            summary = "Atualizar parcialmente perfil do estúdio Maker (PATCH)",
            description = "Atualiza apenas os campos enviados do perfil operacional do estúdio vinculado ao Maker autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/profile")
    public ResponseEntity<BaseResponse<MakerStudioProfileResponse>> patchProfile(
            @Valid @RequestBody PatchMakerProfileRequest request) {
        return BaseResponse.ok(makerStudioService.patchStudioProfile(request));
    }
}
