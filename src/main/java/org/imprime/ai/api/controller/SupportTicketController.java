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
import org.imprime.ai.api.http.request.support.AddSupportMessageRequest;
import org.imprime.ai.api.http.request.support.CreateSupportTicketRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.support.SupportTicketDetailResponse;
import org.imprime.ai.api.http.response.support.SupportTicketMessageResponse;
import org.imprime.ai.api.http.response.support.SupportTicketSummaryResponse;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.service.SupportTicketService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/support-tickets")
@JwtAuthenticated
@Tag(name = "Suporte", description = "Abertura e acompanhamento de chamados e protocolos de atendimento do usuário")
public class SupportTicketController {
    private final SupportTicketService supportTicketService;

    @Operation(
            summary = "Listar chamados de suporte do usuário",
            description = "Retorna página com os protocolos de suporte abertos pelo usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chamados retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<SupportTicketSummaryResponse>>> listTickets(
            @Parameter(description = "Filtrar por status do chamado")
            @RequestParam(required = false) SupportTicketStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(supportTicketService.listTickets(status, pageable));
    }

    @Operation(
            summary = "Abrir novo chamado de suporte",
            description = "Cria um protocolo de suporte vinculado ao usuário autenticado e, opcionalmente, a um pedido."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Chamado criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do chamado inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<SupportTicketDetailResponse>> createTicket(
            @Valid @RequestBody CreateSupportTicketRequest request) {
        return BaseResponse.created(supportTicketService.createTicket(request));
    }

    @Operation(
            summary = "Obter detalhes de um chamado de suporte",
            description = "Retorna os metadados e o histórico de mensagens de um chamado pertencente ao usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chamado encontrado"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chamado pertence a outro usuário", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{ticketGuid}")
    public ResponseEntity<BaseResponse<SupportTicketDetailResponse>> getTicketDetail(
            @Parameter(description = "GUID do chamado de suporte", required = true)
            @PathVariable String ticketGuid) {
        return BaseResponse.ok(supportTicketService.getTicketDetail(ticketGuid));
    }

    @Operation(
            summary = "Adicionar mensagem a um chamado de suporte",
            description = "Envia uma nova réplica no histórico de atendimento do chamado informado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mensagem adicionada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Mensagem vazia ou chamado encerrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chamado pertence a outro usuário", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{ticketGuid}/messages")
    public ResponseEntity<BaseResponse<SupportTicketMessageResponse>> addMessage(
            @Parameter(description = "GUID do chamado de suporte", required = true)
            @PathVariable String ticketGuid,
            @Valid @RequestBody AddSupportMessageRequest request) {
        return BaseResponse.created(supportTicketService.addMessage(ticketGuid, request));
    }
}
