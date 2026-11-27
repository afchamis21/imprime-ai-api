package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.auth.ForgotPasswordRequest;
import org.imprime.ai.api.http.request.auth.LoginRequest;
import org.imprime.ai.api.http.request.auth.RefreshTokenRequest;
import org.imprime.ai.api.http.request.auth.ResetPasswordRequest;
import org.imprime.ai.api.http.request.auth.VerifyEmailRequest;
import org.imprime.ai.api.http.request.user.RegisterUserRequest;
import org.imprime.ai.api.http.response.AuthResponse;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.MessageStatusResponse;
import org.imprime.ai.api.notification.security.AccountSecurityService;
import org.imprime.ai.api.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints públicos para cadastro de usuários, login e renovação de tokens JWT")
public class AuthController {
    private final AuthService authService;
    private final AccountSecurityService accountSecurityService;

    @SkipAuth
    @SecurityRequirements
    @Operation(
            operationId = "register",
            summary = "Cadastrar novo usuário",
            description = "Registra um novo usuário (cliente ou maker com empresa associada), cadastra os endereços correspondentes e retorna os tokens JWT de acesso e atualização."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário cadastrado e autenticado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de cadastro inválidos ou e-mail/documento/telefone já cadastrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@RequestBody RegisterUserRequest request) {
        return BaseResponse.ok(authService.register(request));
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            operationId = "login",
            summary = "Autenticar usuário (Login)",
            description = "Valida as credenciais de e-mail e senha do usuário e emite um par de tokens JWT (`accessToken` e `refreshToken`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticação realizada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas ou usuário inativo", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
        return BaseResponse.ok(authService.login(request));
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            operationId = "refresh",
            summary = "Renovar token de acesso (Refresh Token)",
            description = "Valida um `refreshToken` ativo e não expirado para emitir um novo `accessToken` (e rotacionar o `refreshToken` caso esteja próximo da expiração)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens renovados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, revogado ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(@RequestBody RefreshTokenRequest request) {
        return BaseResponse.ok(authService.refresh(request));
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            operationId = "logout",
            summary = "Encerrar sessão (Logout)",
            description = "Revoga o refresh token informado (quando presente) e marca o Maker associado como offline imediatamente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sessão encerrada com sucesso")
    })
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(authorizationHeader, request);
        return BaseResponse.ok(null);
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            operationId = "forgotPassword",
            summary = "Solicitar redefinição de senha",
            description = "Envia instruções de redefinição de senha caso o e-mail exista e esteja ativo, mantendo resposta genérica anti-enumeração."
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<MessageStatusResponse>> forgotPassword(
            @RequestBody(required = false) ForgotPasswordRequest request) {
        accountSecurityService.requestPasswordReset(request != null ? request.email() : null);
        return BaseResponse.ok(new MessageStatusResponse(
                "Se o e-mail informado estiver cadastrado, enviaremos as instruções para redefinir sua senha."
        ));
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            operationId = "resetPassword",
            summary = "Redefinir senha com token",
            description = "Valida o token de recuperação de uso único, atualiza a senha e revoga sessões anteriores."
    )
    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<MessageStatusResponse>> resetPassword(
            @RequestBody ResetPasswordRequest request) {
        accountSecurityService.resetPassword(request);
        return BaseResponse.ok(new MessageStatusResponse("Senha redefinida com sucesso."));
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            operationId = "verifyEmail",
            summary = "Verificar endereço de e-mail",
            description = "Confirma o endereço de e-mail do usuário a partir do token de uso único enviado na criação ou alteração."
    )
    @PostMapping("/verify-email")
    public ResponseEntity<BaseResponse<MessageStatusResponse>> verifyEmail(
            @RequestBody VerifyEmailRequest request) {
        accountSecurityService.verifyEmail(request != null ? request.token() : null);
        return BaseResponse.ok(new MessageStatusResponse("E-mail verificado com sucesso."));
    }

    @JwtAuthenticated
    @Operation(
            operationId = "resendVerificationEmail",
            summary = "Reenviar link de verificação de e-mail",
            description = "Emite um novo token de confirmação para o endereço de e-mail atual do usuário autenticado."
    )
    @PostMapping("/resend-verification")
    public ResponseEntity<BaseResponse<MessageStatusResponse>> resendVerification() {
        accountSecurityService.resendVerificationForCurrentUser();
        return BaseResponse.ok(new MessageStatusResponse("Enviamos um novo link de verificação para o seu e-mail."));
    }
}
