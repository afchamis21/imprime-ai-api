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
import org.imprime.ai.api.http.request.maker.CreateExpenseRequest;
import org.imprime.ai.api.http.request.maker.CreatePayoutRequest;
import org.imprime.ai.api.http.request.maker.UpdateFinanceRatesRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.FinanceRatesResponse;
import org.imprime.ai.api.http.response.maker.FinanceTransactionResponse;
import org.imprime.ai.api.http.response.maker.MakerFinanceSummaryResponse;
import org.imprime.ai.api.http.response.maker.PayoutResponse;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.service.MakerFinanceService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/finances")
@RequireMaker
@Tag(name = "Maker — Finanças e Repasses", description = "Resumo financeiro, extrato paginado, saques via PIX idempotentes, despesas operacionais e tarifas")
public class MakerFinanceController {
    private final MakerFinanceService makerFinanceService;

    @Operation(
            summary = "Obter resumo financeiro do estúdio",
            description = "Retorna saldos (disponível e pendente), receita, despesas, lucro líquido, breakdown de custos, série mensal e tarifas vigentes."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resumo financeiro retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<MakerFinanceSummaryResponse>> getSummary() {
        return BaseResponse.ok(makerFinanceService.getSummary());
    }

    @Operation(
            summary = "Listar extrato paginado de transações financeiras",
            description = "Retorna página do extrato financeiro do Maker com filtros por tipo, status e intervalo de datas."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Extrato retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<PageResponse<FinanceTransactionResponse>>> listTransactions(
            @Parameter(description = "Filtrar por tipo de transação")
            @RequestParam(required = false) FinanceTransactionType type,
            @Parameter(description = "Filtrar por status financeiro")
            @RequestParam(required = false) FinanceTransactionStatus status,
            @Parameter(description = "Data inicial (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Data final (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerFinanceService.listTransactions(type, status, fromDate, toDate, pageable));
    }

    @Operation(
            summary = "Solicitar saque via PIX",
            description = "Registra solicitação de saque do saldo disponível do Maker com suporte a chave de idempotência (`Idempotency-Key`) para prevenir duplicidade em reenvios."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Saque registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Valor inválido ou saldo insuficiente", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito de concorrência ou chave de idempotência reutilizada com payload divergente", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/payouts")
    public ResponseEntity<BaseResponse<PayoutResponse>> requestPayout(
            @Parameter(description = "Chave UUID de idempotência recomendada para prevenir saques duplicados")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePayoutRequest request) {
        return BaseResponse.created(makerFinanceService.requestPayout(idempotencyKey, request));
    }

    @Operation(
            summary = "Lançar despesa operacional",
            description = "Registra uma despesa operacional (filamento, energia ou manutenção) no extrato financeiro do Maker."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Despesa registrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da despesa inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/expenses")
    public ResponseEntity<BaseResponse<FinanceTransactionResponse>> createExpense(
            @Valid @RequestBody CreateExpenseRequest request) {
        return BaseResponse.created(makerFinanceService.createExpense(request));
    }

    @Operation(
            summary = "Atualizar tarifas de custo operacional e chave PIX padrão",
            description = "Atualiza tarifa de kWh, depreciação horária da frota e chave PIX padrão do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tarifas atualizadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Valores de tarifas ou PIX inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/rates")
    public ResponseEntity<BaseResponse<FinanceRatesResponse>> updateRates(
            @Valid @RequestBody UpdateFinanceRatesRequest request) {
        return BaseResponse.ok(makerFinanceService.updateRates(request));
    }
}
