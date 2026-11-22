package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.auth.LoginRequest;
import org.imprime.ai.api.http.request.auth.RefreshTokenRequest;
import org.imprime.ai.api.http.response.AuthResponse;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.model.dto.FullUserDTO;
import org.imprime.ai.api.service.AdminAuthService;
import org.imprime.ai.api.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin — Autenticação", description = "Autenticação exclusiva de administradores com validação estrita de role ADMIN")
public class AdminAuthController {
    private final AdminAuthService adminAuthService;
    private final UserService userService;

    @SkipAuth
    @SecurityRequirements
    @Operation(
            summary = "Autenticar administrador (Login Backoffice)",
            description = "Valida credenciais, status ativo e perfil obrigatório UserRole.ADMIN antes de emitir tokens JWT."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Administrador autenticado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas ou usuário suspenso/inativo", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil ADMIN", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
        return BaseResponse.ok(adminAuthService.login(request));
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            summary = "Renovar token administrativo (Refresh Token)",
            description = "Renova accessToken garantindo que o usuário permanece ativo e com perfil UserRole.ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens renovados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, revogado ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não possui mais perfil ADMIN", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(@RequestBody RefreshTokenRequest request) {
        return BaseResponse.ok(adminAuthService.refresh(request));
    }

    @RequireAdmin
    @Operation(
            summary = "Encerrar sessão administrativa (Logout)",
            description = "Revoga todos os refresh tokens ativos do administrador autenticado e registra log de auditoria."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sessão administrativa encerrada"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil ADMIN obrigatório", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Map<String, Boolean>>> logout() {
        adminAuthService.logout();
        return BaseResponse.ok(Map.of("loggedOut", true));
    }

    @RequireAdmin
    @Operation(
            summary = "Obter dados do administrador autenticado",
            description = "Retorna os detalhes do administrador autenticado incluindo role e status."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados do administrador retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil ADMIN obrigatório", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<FullUserDTO>> me() {
        return BaseResponse.ok(userService.getFullUserDetails());
    }
}
