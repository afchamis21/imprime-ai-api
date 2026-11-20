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
import org.imprime.ai.api.http.request.maker.CreateModelSourceRequest;
import org.imprime.ai.api.http.request.maker.UpdateModelSourceRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.ModelSourceResponse;
import org.imprime.ai.api.http.response.maker.SyncModelSourceResponse;
import org.imprime.ai.api.service.MakerModelSourceService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/model-sources")
@RequireMaker
@Tag(name = "Maker — Fontes de Modelos", description = "Conectores externos e locais para sincronização automática de bibliotecas 3D")
public class MakerModelSourceController {
    private final MakerModelSourceService makerModelSourceService;

    @Operation(
            summary = "Listar fontes de modelos configuradas",
            description = "Retorna página com os conectores e repositórios externos configurados pelo estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fontes retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<ModelSourceResponse>>> listSources(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerModelSourceService.listSources(pageable));
    }

    @Operation(
            summary = "Conectar nova fonte de modelos",
            description = "Registra uma nova integração de repositório externo ou pasta local para importação de modelos 3D."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Fonte conectada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<ModelSourceResponse>> createSource(
            @Valid @RequestBody CreateModelSourceRequest request) {
        return BaseResponse.created(makerModelSourceService.createSource(request));
    }

    @Operation(
            summary = "Atualizar configurações de uma fonte de modelos (PATCH)",
            description = "Atualiza parcialmente URL, ativação, intervalo de sincronização ou credenciais de uma fonte conectada."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fonte atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Fonte pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fonte de modelos não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{sourceGuid}")
    public ResponseEntity<BaseResponse<ModelSourceResponse>> updateSource(
            @Parameter(description = "GUID da fonte de modelos", required = true)
            @PathVariable String sourceGuid,
            @Valid @RequestBody UpdateModelSourceRequest request) {
        return BaseResponse.ok(makerModelSourceService.updateSource(sourceGuid, request));
    }

    @Operation(
            summary = "Desconectar fonte de modelos",
            description = "Remove logicamente uma integração de fonte de modelos do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Fonte removida com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Fonte pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fonte de modelos não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{sourceGuid}")
    public ResponseEntity<Void> deleteSource(
            @Parameter(description = "GUID da fonte de modelos", required = true)
            @PathVariable String sourceGuid) {
        makerModelSourceService.deleteSource(sourceGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Sincronizar fonte de modelos manualmente",
            description = "Dispara a sincronização imediata com o provedor externo e importa novos modelos para o Print Archive do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sincronização concluída"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Fonte pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fonte de modelos não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "502", description = "Falha na comunicação com o provedor externo", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{sourceGuid}/sync")
    public ResponseEntity<BaseResponse<SyncModelSourceResponse>> syncSource(
            @Parameter(description = "GUID da fonte de modelos", required = true)
            @PathVariable String sourceGuid) {
        return BaseResponse.ok(makerModelSourceService.syncSource(sourceGuid));
    }
}
