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
import org.imprime.ai.api.http.request.maker.CreatePrinterRequest;
import org.imprime.ai.api.http.request.maker.PrinterCommandRequest;
import org.imprime.ai.api.http.request.maker.UpdatePrinterRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.PrinterResponse;
import org.imprime.ai.api.http.response.maker.RegenerateApiKeyResponse;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.service.MakerPrinterService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/printers")
@RequireMaker
@Tag(name = "Maker — Impressoras e Telemetria", description = "Gestão da frota de impressoras 3D, telemetria, slots AMS, API keys e comandos remotos")
public class MakerPrinterController {
    private final MakerPrinterService makerPrinterService;

    @Operation(
            summary = "Listar impressoras da frota",
            description = "Retorna página com as impressoras cadastradas pelo estúdio autenticado e respectiva telemetria."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Impressoras retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<PrinterResponse>>> listPrinters(
            @Parameter(description = "Filtrar por status operacional da impressora")
            @RequestParam(required = false) PrinterStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerPrinterService.listPrinters(status, pageable));
    }

    @Operation(
            summary = "Obter detalhes de uma impressora",
            description = "Retorna detalhes, telemetria e configuração (com API key mascarada) de uma impressora do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Impressora encontrada"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Impressora pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Impressora não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{printerGuid}")
    public ResponseEntity<BaseResponse<PrinterResponse>> getPrinter(
            @Parameter(description = "GUID da impressora", required = true)
            @PathVariable String printerGuid) {
        return BaseResponse.ok(makerPrinterService.getPrinter(printerGuid));
    }

    @Operation(
            summary = "Cadastrar nova impressora",
            description = "Adiciona uma nova impressora 3D à frota do estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Impressora cadastrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<PrinterResponse>> createPrinter(
            @Valid @RequestBody CreatePrinterRequest request) {
        return BaseResponse.created(makerPrinterService.createPrinter(request));
    }

    @Operation(
            summary = "Atualizar configurações da impressora (PATCH)",
            description = "Atualiza parâmetros físicos, status, slots AMS, auto-aceite, integração API e filamentos vinculados."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Impressora atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Impressora pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Impressora não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{printerGuid}")
    public ResponseEntity<BaseResponse<PrinterResponse>> updatePrinter(
            @Parameter(description = "GUID da impressora", required = true)
            @PathVariable String printerGuid,
            @Valid @RequestBody UpdatePrinterRequest request) {
        return BaseResponse.ok(makerPrinterService.updatePrinter(printerGuid, request));
    }

    @Operation(
            summary = "Remover impressora da frota",
            description = "Remove uma impressora ociosa da frota do Maker."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Impressora removida com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Impressora pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Impressora não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Impressora está em impressão ativa e não pode ser removida", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{printerGuid}")
    public ResponseEntity<Void> deletePrinter(
            @Parameter(description = "GUID da impressora", required = true)
            @PathVariable String printerGuid) {
        makerPrinterService.deletePrinter(printerGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Gerar/rotacionar chave de API da impressora",
            description = "Gera uma nova chave de API externa para agentes locais (OctoPrint, Moonraker, Bambu LAN). A chave completa é exibida uma única vez nesta resposta."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Nova API Key gerada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Impressora pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Impressora não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{printerGuid}/api-key")
    public ResponseEntity<BaseResponse<RegenerateApiKeyResponse>> regenerateApiKey(
            @Parameter(description = "GUID da impressora", required = true)
            @PathVariable String printerGuid) {
        return BaseResponse.ok(makerPrinterService.regenerateApiKey(printerGuid));
    }

    @Operation(
            summary = "Enviar comando remoto para a impressora",
            description = "Executa comando operacional remoto (`PAUSE`, `RESUME`, `STOP`, `PREHEAT`, `COOLDOWN`, `SET_ONLINE`, `SET_OFFLINE`, `SET_MAINTENANCE`) sincronizando com a fila ativa."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comando executado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Comando inválido para o estado atual da máquina", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Impressora pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Impressora não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual da impressora", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{printerGuid}/commands")
    public ResponseEntity<BaseResponse<PrinterResponse>> sendCommand(
            @Parameter(description = "GUID da impressora", required = true)
            @PathVariable String printerGuid,
            @Valid @RequestBody PrinterCommandRequest request) {
        return BaseResponse.ok(makerPrinterService.sendCommand(printerGuid, request));
    }
}
