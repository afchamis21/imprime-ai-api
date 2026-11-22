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
import org.imprime.ai.api.http.response.admin.AdminAuditLogDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminAuditLogSummaryResponse;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.service.AdminAuditService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/audit-logs")
@RequireAdmin
@Tag(name = "Admin — Auditoria", description = "Consulta paginada e detalhada dos registros de auditoria das ações administrativas")
public class AdminAuditController {
    private final AdminAuditService adminAuditService;

    @Operation(
            summary = "Listar logs de auditoria administrativa",
            description = "Retorna página com os registros de auditoria filtráveis por administrador, ação, tipo/GUID do alvo, período e busca textual."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logs retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de filtro inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil ADMIN obrigatório", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminAuditLogSummaryResponse>>> listAuditLogs(
            @Parameter(description = "Filtrar por GUID do administrador ator")
            @RequestParam(required = false) String adminGuid,
            @Parameter(description = "Filtrar por ação executada")
            @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Filtrar por tipo de entidade alvo")
            @RequestParam(required = false) String targetType,
            @Parameter(description = "Filtrar por GUID da entidade alvo")
            @RequestParam(required = false) String targetGuid,
            @Parameter(description = "Data inicial UTC (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Data final UTC (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Busca textual em administrador, alvo, motivo ou ação")
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminAuditService.listAuditLogs(
                adminGuid, action, targetType, targetGuid, fromDate, toDate, q, pageable
        ));
    }

    @Operation(
            summary = "Obter detalhes de um log de auditoria",
            description = "Retorna o registro completo de auditoria, incluindo snapshots JSON de antes e depois da ação."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Log encontrado"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil ADMIN obrigatório", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Registro de auditoria não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{logGuid}")
    public ResponseEntity<BaseResponse<AdminAuditLogDetailResponse>> getAuditLogDetail(
            @Parameter(description = "GUID do log de auditoria", required = true)
            @PathVariable String logGuid
    ) {
        return BaseResponse.ok(adminAuditService.getAuditLogDetail(logGuid));
    }
}
