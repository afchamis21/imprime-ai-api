package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminCreateUserRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateUserRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateUserStatusRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminUserDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminUserSummaryResponse;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.service.AdminUserService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@RequireAdmin
@Tag(name = "Admin — Usuários", description = "Gestão administrativa de Compradores, Designers, Makers e Administradores")
public class AdminUserController {
    private final AdminUserService adminUserService;

    @Operation(summary = "Listar usuários", description = "Retorna página de usuários filtrada por busca textual, perfil (role) e status.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil ADMIN obrigatório", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminUserSummaryResponse>>> listUsers(
            @Parameter(description = "Busca por nome, e-mail, GUID, telefone ou empresa")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filtrar por perfil de acesso")
            @RequestParam(required = false) UserRole role,
            @Parameter(description = "Filtrar por status cadastral")
            @RequestParam(required = false) StatusCd status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminUserService.listUsers(q, role, status, pageable));
    }

    @Operation(summary = "Obter detalhes de usuário", description = "Retorna dados cadastrais, endereços e contadores de atividade do usuário.")
    @GetMapping("/{userGuid}")
    public ResponseEntity<BaseResponse<AdminUserDetailResponse>> getUserDetail(@PathVariable String userGuid) {
        return BaseResponse.ok(adminUserService.getUserDetail(userGuid));
    }

    @Operation(summary = "Criar usuário", description = "Cria novo usuário (Comprador, Designer, Maker ou Admin) diretamente pelo backoffice.")
    @PostMapping
    public ResponseEntity<BaseResponse<AdminUserDetailResponse>> createUser(
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return BaseResponse.created(adminUserService.createUser(request));
    }

    @Operation(summary = "Atualizar usuário", description = "Atualiza dados cadastrais e perfil (role) do usuário, invalidando cache.")
    @PutMapping("/{userGuid}")
    public ResponseEntity<BaseResponse<AdminUserDetailResponse>> updateUser(
            @PathVariable String userGuid,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        return BaseResponse.ok(adminUserService.updateUser(userGuid, request));
    }

    @Operation(summary = "Suspender ou reativar usuário", description = "Altera o status do usuário (motivo obrigatório em suspensões) e revoga tokens ativos.")
    @PatchMapping("/{userGuid}/status")
    public ResponseEntity<BaseResponse<AdminUserDetailResponse>> updateUserStatus(
            @PathVariable String userGuid,
            @Valid @RequestBody AdminUpdateUserStatusRequest request
    ) {
        return BaseResponse.ok(adminUserService.updateUserStatus(userGuid, request));
    }

    @Operation(summary = "Excluir usuário (soft delete)", description = "Marca o usuário como DELETED, invalida cache e revoga todos os tokens ativos.")
    @DeleteMapping("/{userGuid}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String userGuid,
            @RequestParam(required = false) String reason
    ) {
        adminUserService.deleteUser(userGuid, reason);
        return ResponseEntity.noContent().build();
    }
}
