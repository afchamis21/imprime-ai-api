package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminDashboardSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminSlaAlertResponse;
import org.imprime.ai.api.service.AdminDashboardService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/dashboard")
@RequireAdmin
@Tag(name = "Admin — Dashboard e SLA", description = "Indicadores executivos consolidados do marketplace e alertas de SLA atrasado")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    @Operation(
            summary = "Obter KPIs e série temporal do dashboard administrativo",
            description = "Retorna GMV, receita de taxas da plataforma, pedidos em andamento, tickets/disputas abertos, filas pendentes e série temporal."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Indicadores retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil ADMIN obrigatório", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<AdminDashboardSummaryResponse>> getSummary(
            @Parameter(description = "Período de análise (`7d`, `30d`, `90d`, `all`)")
            @RequestParam(defaultValue = "30d") String range
    ) {
        return BaseResponse.ok(adminDashboardService.getSummary(range));
    }

    @Operation(
            summary = "Listar alertas de pedidos com SLA atrasado",
            description = "Retorna lista paginada de pedidos com atraso operacional ordenados por severidade e horas de atraso."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alertas retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil ADMIN obrigatório", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/sla-alerts")
    public ResponseEntity<BaseResponse<PageResponse<AdminSlaAlertResponse>>> listSlaAlerts(
            @Parameter(description = "Filtrar por severidade (`CRITICAL` ou `WARNING`)")
            @RequestParam(required = false) String severity,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminDashboardService.listSlaAlerts(severity, pageable));
    }
}
