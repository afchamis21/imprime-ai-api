package org.imprime.ai.api.controller.maker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireMaker;
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteModulesRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsBundleResponse;
import org.imprime.ai.api.service.MakerQuoteSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/quote-settings")
@RequireMaker
@Tag(name = "Maker — Orçamentos 3D", description = "Configuração modular dos parâmetros de cálculo de orçamento de impressão 3D do estúdio")
public class MakerQuoteSettingsController {
    private final MakerQuoteSettingsService makerQuoteSettingsService;

    @Operation(
            summary = "Obter módulos e parâmetros de orçamento do estúdio",
            description = "Retorna o catálogo de módulos mesclado às customizações do estúdio e tarifas de energia/depreciação da empresa."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Módulos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<MakerQuoteSettingsBundleResponse>> getSettings() {
        return BaseResponse.ok(makerQuoteSettingsService.getSettings());
    }

    @Operation(
            summary = "Salvar configuração modular de orçamento do estúdio",
            description = "Atualiza ativação, ordem e configurações JSON dos módulos, valida exclusividade mútua e sincroniza tarifa de kWh e depreciação horária com o módulo financeiro."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Módulos salvos com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, violação de grupo exclusivo ou soma de margem e taxas incompatível", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping
    public ResponseEntity<BaseResponse<MakerQuoteSettingsBundleResponse>> updateSettings(
            @Valid @RequestBody UpdateMakerQuoteModulesRequest request) {
        return BaseResponse.ok(makerQuoteSettingsService.updateSettings(request));
    }
}
