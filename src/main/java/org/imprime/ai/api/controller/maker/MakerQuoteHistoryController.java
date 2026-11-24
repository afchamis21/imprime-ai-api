package org.imprime.ai.api.controller.maker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireMaker;
import org.imprime.ai.api.http.request.maker.CalculateMakerQuoteRequest;
import org.imprime.ai.api.http.request.maker.CreateMakerQuoteRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteStatusRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteCalculationResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteHistoryResponse;
import org.imprime.ai.api.model.enums.QuoteHistoryStatus;
import org.imprime.ai.api.service.MakerQuoteCalculationService;
import org.imprime.ai.api.service.MakerQuoteHistoryService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/quotes")
@RequireMaker
@Tag(name = "Maker — Orçamentos 3D", description = "Histórico de orçamentos calculados e salvos pelo estúdio")
public class MakerQuoteHistoryController {
    private final MakerQuoteHistoryService makerQuoteHistoryService;
    private final MakerQuoteCalculationService makerQuoteCalculationService;

    @Operation(
            summary = "Calcular orçamento em tempo real",
            description = "Executa o motor modular de orçamento 3D combinando catálogo global, módulos configurados do estúdio e overrides pontuais."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cálculo realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/calculate")
    public ResponseEntity<BaseResponse<MakerQuoteCalculationResponse>> calculateQuote(
            @Valid @RequestBody CalculateMakerQuoteRequest request) {
        return BaseResponse.ok(makerQuoteCalculationService.calculateQuote(request));
    }

    @Operation(
            summary = "Listar histórico de orçamentos do Maker",
            description = "Retorna página com os orçamentos salvos pelo estúdio autenticado, com filtro opcional por status e busca."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orçamentos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<MakerQuoteHistoryResponse>>> listQuotes(
            @Parameter(description = "Filtrar por status do orçamento")
            @RequestParam(required = false) QuoteHistoryStatus status,
            @Parameter(description = "Busca por título ou nome do cliente")
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerQuoteHistoryService.listQuotes(status, q, pageable));
    }

    @Operation(
            summary = "Salvar novo orçamento no histórico",
            description = "Registra um snapshot completo do orçamento calculado (entradas, parâmetros e resultado) no histórico do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Orçamento salvo com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados ou snapshots inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<MakerQuoteHistoryResponse>> createQuote(
            @Valid @RequestBody CreateMakerQuoteRequest request) {
        return BaseResponse.created(makerQuoteHistoryService.createQuote(request));
    }

    @Operation(
            summary = "Atualizar status de um orçamento salvo",
            description = "Altera o status comercial (DRAFT, SENT, APPROVED, REJECTED) de um orçamento do histórico."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Orçamento pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{guid}/status")
    public ResponseEntity<BaseResponse<MakerQuoteHistoryResponse>> updateQuoteStatus(
            @Parameter(description = "GUID do orçamento", required = true)
            @PathVariable String guid,
            @Valid @RequestBody UpdateMakerQuoteStatusRequest request) {
        return BaseResponse.ok(makerQuoteHistoryService.updateQuoteStatus(guid, request));
    }

    @Operation(
            summary = "Excluir orçamento do histórico",
            description = "Remove logicamente um orçamento salvo do histórico do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Orçamento removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Orçamento pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{guid}")
    public ResponseEntity<Void> deleteQuote(
            @Parameter(description = "GUID do orçamento", required = true)
            @PathVariable String guid) {
        makerQuoteHistoryService.deleteQuote(guid);
        return ResponseEntity.noContent().build();
    }
}
