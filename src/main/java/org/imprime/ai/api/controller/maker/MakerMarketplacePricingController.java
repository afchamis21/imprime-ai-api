package org.imprime.ai.api.controller.maker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireMaker;
import org.imprime.ai.api.http.request.maker.UpdateMakerMarketplacePricingRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.maker.MakerMarketplacePricingResponse;
import org.imprime.ai.api.service.MakerMarketplacePricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/maker/marketplace-pricing")
@RequiredArgsConstructor
@RequireMaker
@Tag(name = "Maker - Tabela do Marketplace", description = "Configuração de precificação instantânea, hora-máquina, suportes, taxas e preços por material")
public class MakerMarketplacePricingController {

    private final MakerMarketplacePricingService makerMarketplacePricingService;

    @GetMapping
    @Operation(summary = "Obtém a tabela pública de precificação instantânea e preview de velocidade das impressoras do Maker")
    public ResponseEntity<BaseResponse<MakerMarketplacePricingResponse>> getMyPricing() {
        return BaseResponse.ok(makerMarketplacePricingService.getMyPricing());
    }

    @PutMapping
    @Operation(summary = "Atualiza a tabela de precificação instantânea e preços por material do Maker")
    public ResponseEntity<BaseResponse<MakerMarketplacePricingResponse>> updateMyPricing(
            @Valid @RequestBody UpdateMakerMarketplacePricingRequest request
    ) {
        return BaseResponse.ok(makerMarketplacePricingService.updateMyPricing(request));
    }
}
