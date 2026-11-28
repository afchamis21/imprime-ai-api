package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.OptionalAuth;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.order.InstantPrintQuoteRequest;
import org.imprime.ai.api.http.request.print.PrintEstimateRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse;
import org.imprime.ai.api.http.response.print.PrintEstimateResponse;
import org.imprime.ai.api.http.response.print.PrintPricingConfigResponse;
import org.imprime.ai.api.service.InstantPrintQuoteService;
import org.imprime.ai.api.service.PrintPricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/print")
@Tag(name = "Impressão Sob Demanda (Precificação)", description = "Configurações públicas de precificação e cálculo autoritativo de estimativa para impressão 3D")
public class PrintPricingController {
    private final PrintPricingService printPricingService;
    private InstantPrintQuoteService instantPrintQuoteService;

    @Autowired(required = false)
    public void setInstantPrintQuoteService(InstantPrintQuoteService instantPrintQuoteService) {
        this.instantPrintQuoteService = instantPrintQuoteService;
    }

    @Operation(
            summary = "Obter parâmetros públicos de precificação 3D",
            description = "Retorna taxa base, valor por grama, valor por hora e multiplicadores por material para exibição e cálculo no fatiador do navegador."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Parâmetros retornados com sucesso"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/pricing")
    @SkipAuth
    @SecurityRequirements
    public ResponseEntity<BaseResponse<PrintPricingConfigResponse>> getPricingConfig() {
        return BaseResponse.ok(printPricingService.getPricingConfig());
    }

    @Operation(
            summary = "Calcular estimativa de preço de impressão",
            description = "Calcula de forma autoritativa no backend o valor estimado da impressão a partir do peso em gramas, tempo estimado, material e quantidade."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estimativa calculada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de estimativa inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/estimate")
    @SkipAuth
    @SecurityRequirements
    public ResponseEntity<BaseResponse<PrintEstimateResponse>> estimatePrint(
            @Valid @RequestBody PrintEstimateRequest request) {
        return BaseResponse.ok(printPricingService.estimatePrint(request));
    }

    @Operation(
            summary = "Calcular cotação instantânea por vendedor próximo",
            description = "Calcula preços, tempos estimados por velocidade da impressora e prazos em lote para makers próximos usando o DNA único do modelo."
    )
    @PostMapping("/instant-quotes")
    @OptionalAuth
    public ResponseEntity<BaseResponse<InstantPrintQuoteResponse>> instantQuotes(
            @Valid @RequestBody InstantPrintQuoteRequest request
    ) {
        return BaseResponse.ok(instantPrintQuoteService.quoteNearbyMakers(request));
    }
}
