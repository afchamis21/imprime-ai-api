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
import org.imprime.ai.api.http.request.maker.CreateBomItemRequest;
import org.imprime.ai.api.http.request.maker.CreateMakerProjectRequest;
import org.imprime.ai.api.http.request.maker.QueueNextPlateRequest;
import org.imprime.ai.api.http.request.maker.UpdateBomItemRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProjectRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.BomItemResponse;
import org.imprime.ai.api.http.response.maker.MakerProjectResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.ProjectStatus;
import org.imprime.ai.api.service.MakerProjectService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/projects")
@RequireMaker
@Tag(name = "Maker — Projetos Multi-Peça (BOM)", description = "Gestão de projetos complexos multi-mesa, insumos granulares (BOM) e enfileiramento de próximas mesas")
public class MakerProjectController {
    private final MakerProjectService makerProjectService;

    @Operation(
            summary = "Listar projetos multi-peça do Maker",
            description = "Retorna página com os projetos multi-peça do estúdio autenticado, com filtros por status e busca."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projetos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<MakerProjectResponse>>> listProjects(
            @Parameter(description = "Filtrar por status do projeto")
            @RequestParam(required = false) ProjectStatus status,
            @Parameter(description = "Busca por nome do projeto ou cliente")
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerProjectService.listProjects(status, q, pageable));
    }

    @Operation(
            summary = "Obter detalhes de um projeto multi-peça",
            description = "Retorna detalhes do projeto, progresso de mesas/peças, arquivos vinculados e itens da BOM."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projeto encontrado"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Projeto pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Projeto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{projectGuid}")
    public ResponseEntity<BaseResponse<MakerProjectResponse>> getProject(
            @Parameter(description = "GUID do projeto", required = true)
            @PathVariable String projectGuid) {
        return BaseResponse.ok(makerProjectService.getProject(projectGuid));
    }

    @Operation(
            summary = "Criar novo projeto multi-peça",
            description = "Cria um projeto multi-peça com acompanhamento de mesas, prazos e orçamento."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Projeto criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<MakerProjectResponse>> createProject(
            @Valid @RequestBody CreateMakerProjectRequest request) {
        return BaseResponse.created(makerProjectService.createProject(request));
    }

    @Operation(
            summary = "Atualizar projeto multi-peça (PATCH)",
            description = "Atualiza parcialmente metadados, status ou contagem de mesas/peças concluídas do projeto."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projeto atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Projeto pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Projeto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{projectGuid}")
    public ResponseEntity<BaseResponse<MakerProjectResponse>> updateProject(
            @Parameter(description = "GUID do projeto", required = true)
            @PathVariable String projectGuid,
            @Valid @RequestBody UpdateMakerProjectRequest request) {
        return BaseResponse.ok(makerProjectService.updateProject(projectGuid, request));
    }

    @Operation(
            summary = "Excluir projeto multi-peça",
            description = "Remove logicamente um projeto multi-peça do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Projeto removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Projeto pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Projeto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{projectGuid}")
    public ResponseEntity<Void> deleteProject(
            @Parameter(description = "GUID do projeto", required = true)
            @PathVariable String projectGuid) {
        makerProjectService.deleteProject(projectGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Adicionar item à BOM do projeto",
            description = "Adiciona um novo insumo extra à Lista de Materiais (BOM) do projeto de forma granular."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Item da BOM criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Projeto pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Projeto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{projectGuid}/bom")
    public ResponseEntity<BaseResponse<BomItemResponse>> addBomItem(
            @Parameter(description = "GUID do projeto", required = true)
            @PathVariable String projectGuid,
            @Valid @RequestBody CreateBomItemRequest request) {
        return BaseResponse.created(makerProjectService.addBomItem(projectGuid, request));
    }

    @Operation(
            summary = "Atualizar item da BOM do projeto (PATCH)",
            description = "Atualiza parcialmente quantidade, custo ou estado de aquisição (`acquired`) de um item específico da BOM."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item da BOM atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Projeto pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Projeto ou item da BOM não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{projectGuid}/bom/{bomItemGuid}")
    public ResponseEntity<BaseResponse<BomItemResponse>> updateBomItem(
            @Parameter(description = "GUID do projeto", required = true)
            @PathVariable String projectGuid,
            @Parameter(description = "GUID do item da BOM", required = true)
            @PathVariable String bomItemGuid,
            @Valid @RequestBody UpdateBomItemRequest request) {
        return BaseResponse.ok(makerProjectService.updateBomItem(projectGuid, bomItemGuid, request));
    }

    @Operation(
            summary = "Remover item da BOM do projeto",
            description = "Remove um insumo da Lista de Materiais (BOM) do projeto."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Item da BOM removido"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Projeto pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Projeto ou item da BOM não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{projectGuid}/bom/{bomItemGuid}")
    public ResponseEntity<Void> deleteBomItem(
            @Parameter(description = "GUID do projeto", required = true)
            @PathVariable String projectGuid,
            @Parameter(description = "GUID do item da BOM", required = true)
            @PathVariable String bomItemGuid) {
        makerProjectService.deleteBomItem(projectGuid, bomItemGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Enfileirar próxima mesa do projeto",
            description = "Cria automaticamente um job na fila de impressão para a próxima mesa pendente do projeto e atualiza seu status para `IN_PROGRESS`."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Próxima mesa enfileirada na fila de impressão"),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Projeto pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Projeto ou impressora não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Todas as mesas do projeto já foram concluídas", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{projectGuid}/queue-next-plate")
    public ResponseEntity<BaseResponse<PrintQueueItemResponse>> queueNextPlate(
            @Parameter(description = "GUID do projeto", required = true)
            @PathVariable String projectGuid,
            @Valid @RequestBody(required = false) QueueNextPlateRequest request) {
        return BaseResponse.created(makerProjectService.queueNextPlate(projectGuid, request));
    }
}
