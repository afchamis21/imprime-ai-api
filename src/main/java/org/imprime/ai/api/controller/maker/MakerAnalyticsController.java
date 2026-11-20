package org.imprime.ai.api.controller.maker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireMaker;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.maker.MakerStatsResponse;
import org.imprime.ai.api.model.enums.StatsRange;
import org.imprime.ai.api.service.MakerAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/stats")
@RequireMaker
@Tag(name = "Maker — Estatísticas e Produção", description = "Métricas consolidadas de impressão, eficiência, consumo de material e energia")
public class MakerAnalyticsController {
    private final MakerAnalyticsService makerAnalyticsService;

    @Operation(
            summary = "Obter estatísticas operacionais do Maker",
            description = "Retorna KPIs de produção, taxa de sucesso, consumo de filamentos e energia, utilização da frota, tendência diária e mapa de calor."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Intervalo de datas ou range inválido", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<MakerStatsResponse>> getStats(
            @Parameter(description = "Janela temporal predefinida")
            @RequestParam(defaultValue = "LAST_30_DAYS") StatsRange range,
            @Parameter(description = "Data inicial customizada (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Data final customizada (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return BaseResponse.ok(makerAnalyticsService.getStats(range, from, to));
    }
}
