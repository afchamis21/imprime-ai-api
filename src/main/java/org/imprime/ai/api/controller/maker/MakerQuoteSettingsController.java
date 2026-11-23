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
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteSettingsRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsResponse;
import org.imprime.ai.api.service.MakerQuoteSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/quote-settings")
@RequireMaker
@Tag(name = "Maker — Orçamentos 3D", description = "Configuração dos parâmetros fixos para cálculo de orçamento de impressão 3D do estúdio")
public class MakerQuoteSettingsController {
    private final MakerQuoteSettingsService makerQuoteSettingsService;

    @Operation(
            summary = "Obter parâmetros fixos de orçamento do estúdio",
            description = "Retorna os parâmetros configurados para orçamentos de impressão 3D combinados com as tarifas de energia e depreciação vigentes do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Parâmetros retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<MakerQuoteSettingsResponse>> getSettings() {
        return BaseResponse.ok(makerQuoteSettingsService.getSettings());
    }

    @Operation(
            summary = "Salvar parâmetros fixos de orçamento do estúdio",
            description = "Atualiza os parâmetros fixos da calculadora de orçamento e sincroniza tarifa de kWh e depreciação horária com o módulo financeiro."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Parâmetros salvos com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou soma de margem e taxas incompatível", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping
    public ResponseEntity<BaseResponse<MakerQuoteSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateMakerQuoteSettingsRequest request) {
        return BaseResponse.ok(makerQuoteSettingsService.updateSettings(request));
    }
}
