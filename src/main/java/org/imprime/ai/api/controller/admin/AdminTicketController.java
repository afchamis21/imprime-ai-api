package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminTicketReplyRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateTicketRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminTicketDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminTicketSummaryResponse;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.imprime.ai.api.service.AdminTicketService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tickets")
@RequireAdmin
@Tag(name = "Admin — Tickets & Disputas", description = "Central de atendimento administrativo, análise de evidências e histórico de chat em disputas 3D")
public class AdminTicketController {
    private final AdminTicketService adminTicketService;

    @Operation(summary = "Listar tickets e disputas da plataforma")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminTicketSummaryResponse>>> listTickets(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(required = false) SupportTicketCategory category,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Boolean isDispute,
            @RequestParam(required = false) String assignedAdminGuid,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminTicketService.listTickets(
                q, status, category, priority, isDispute, assignedAdminGuid, pageable
        ));
    }

    @Operation(summary = "Obter detalhes completos do ticket ou disputa com anexos de evidência e chat do pedido")
    @GetMapping("/{ticketGuid}")
    public ResponseEntity<BaseResponse<AdminTicketDetailResponse>> getTicketDetail(@PathVariable String ticketGuid) {
        return BaseResponse.ok(adminTicketService.getTicketDetail(ticketGuid));
    }

    @Operation(summary = "Enviar resposta oficial da administração no ticket ou disputa")
    @PostMapping("/{ticketGuid}/messages")
    public ResponseEntity<BaseResponse<AdminTicketDetailResponse>> replyTicket(
            @PathVariable String ticketGuid,
            @Valid @RequestBody AdminTicketReplyRequest request
    ) {
        return BaseResponse.ok(adminTicketService.replyTicket(ticketGuid, request));
    }

    @Operation(summary = "Atualizar status, prioridade, responsável ou resolução do ticket")
    @PatchMapping("/{ticketGuid}")
    public ResponseEntity<BaseResponse<AdminTicketDetailResponse>> updateTicket(
            @PathVariable String ticketGuid,
            @Valid @RequestBody AdminUpdateTicketRequest request
    ) {
        return BaseResponse.ok(adminTicketService.updateTicket(ticketGuid, request));
    }
}
