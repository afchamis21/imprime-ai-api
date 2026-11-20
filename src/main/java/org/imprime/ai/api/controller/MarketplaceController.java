package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.marketplace.MarketplaceStatsResponse;
import org.imprime.ai.api.service.MarketplaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/marketplace")
@SkipAuth
@SecurityRequirements
@Tag(name = "Marketplace", description = "Indicadores públicos consolidados da plataforma Imprime AI")
public class MarketplaceController {
    private final MarketplaceService marketplaceService;

    @Operation(
            summary = "Obter estatísticas públicas do marketplace",
            description = "Retorna métricas agregadas públicas de Makers ativos, avaliação média, faixa de entrega em dias e total de pedidos concluídos para exibição nas páginas públicas."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/stats")
    public ResponseEntity<BaseResponse<MarketplaceStatsResponse>> getStats() {
        return BaseResponse.ok(marketplaceService.getStats());
    }
}
