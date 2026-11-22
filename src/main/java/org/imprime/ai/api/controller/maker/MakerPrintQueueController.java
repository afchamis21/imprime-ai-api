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
import org.imprime.ai.api.http.request.maker.CreatePrintQueueItemRequest;
import org.imprime.ai.api.http.request.maker.PrintJobTransitionRequest;
import org.imprime.ai.api.http.request.maker.ReorderPrintQueueRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.service.MakerPrintQueueService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/queue")
@RequireMaker
@Tag(name = "Maker — Fila de Impressão", description = "Gerenciamento da fila de jobs de impressão, transições de estado e reordenação atômica")
public class MakerPrintQueueController {
    private final MakerPrintQueueService makerPrintQueueService;

    @Operation(
            summary = "Listar jobs na fila de impressão",
            description = "Retorna página ordenada dos jobs da fila de impressão do Maker autenticado, com filtros por status e impressora."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fila retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<PrintQueueItemResponse>>> listQueue(
            @Parameter(description = "Filtrar por status do job")
            @RequestParam(required = false) PrintJobStatus status,
            @Parameter(description = "Filtrar por GUID da impressora")
            @RequestParam(required = false) String printerGuid,
            @Parameter(description = "Quando true, retorna apenas jobs ativos (`QUEUED`, `PRINTING`, `PAUSED`)")
            @RequestParam(required = false) Boolean activeOnly,
            @Parameter(description = "Filtrar por status atual da venda vinculada (ex.: `IN_PRODUCTION`)")
            @RequestParam(required = false) OrderStatusCd orderStatus,
            @ParameterObject @PageableDefault(size = 50, sort = "position") Pageable pageable) {
        return BaseResponse.ok(makerPrintQueueService.listQueue(status, printerGuid, activeOnly, orderStatus, pageable));
    }

    @Operation(
            summary = "Enfileirar novo job de impressão",
            description = "Adiciona manualmente um job à fila de impressão vinculado a uma impressora e opcionalmente a um arquivo do acervo, filamento ou pedido."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Job adicionado à fila"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Impressora ou arquivo não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<PrintQueueItemResponse>> enqueueJob(
            @Valid @RequestBody CreatePrintQueueItemRequest request) {
        return BaseResponse.created(makerPrintQueueService.enqueueJob(request));
    }

    @Operation(
            summary = "Remover job da fila de impressão",
            description = "Remove o job da fila, reindexa posições e libera a impressora caso estivesse em execução."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Job removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Job pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{jobGuid}")
    public ResponseEntity<Void> deleteJob(
            @Parameter(description = "GUID do job na fila", required = true)
            @PathVariable String jobGuid) {
        makerPrintQueueService.deleteJob(jobGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Executar transição de estado em um job da fila",
            description = "Executa transições (`START`, `PAUSE`, `RESUME`, `COMPLETE`, `FAIL`, `RETRY`, `CANCEL`) atualizando estoque de filamento, telemetria da impressora e progresso do projeto vinculado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transição aplicada ao job"),
            @ApiResponse(responseCode = "400", description = "Transição inválida para o estado atual", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Job pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do job ou impressora ocupada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{jobGuid}/transitions")
    public ResponseEntity<BaseResponse<PrintQueueItemResponse>> transitionJob(
            @Parameter(description = "GUID do job na fila", required = true)
            @PathVariable String jobGuid,
            @Valid @RequestBody PrintJobTransitionRequest request) {
        return BaseResponse.ok(makerPrintQueueService.transitionJob(jobGuid, request));
    }

    @Operation(
            summary = "Reordenar fila de impressão atomicamente",
            description = "Define de forma idempotente e atômica a nova ordem completa dos jobs ativos a partir da lista ordenada de `orderedJobGuids`."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fila reordenada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Lista de GUIDs inválida ou incompleta", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/reorder")
    public ResponseEntity<BaseResponse<List<PrintQueueItemResponse>>> reorderQueue(
            @Valid @RequestBody ReorderPrintQueueRequest request) {
        return BaseResponse.ok(makerPrintQueueService.reorderQueue(request));
    }

    @Operation(
            summary = "Atualizar job da fila",
            description = "Atualiza parcialmente um job: troca/remoção da impressora designada (somente jobs não iniciados), prioridade e cópias."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Job ou impressora pertence a outro estúdio", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job ou impressora não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Job em execução não pode trocar de impressora", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{jobGuid}")
    public ResponseEntity<BaseResponse<PrintQueueItemResponse>> updateJob(
            @Parameter(description = "GUID do job na fila", required = true)
            @PathVariable String jobGuid,
            @Valid @RequestBody org.imprime.ai.api.http.request.maker.UpdatePrintQueueItemRequest request) {
        return BaseResponse.ok(makerPrintQueueService.updateJob(jobGuid, request));
    }
}
