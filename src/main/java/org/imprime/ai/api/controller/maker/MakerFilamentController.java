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
import org.imprime.ai.api.http.request.maker.AdjustFilamentStockRequest;
import org.imprime.ai.api.http.request.maker.CreateFilamentRequest;
import org.imprime.ai.api.http.request.maker.UpdateFilamentRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.FilamentResponse;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.service.MakerFilamentService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/filaments")
@RequireMaker
@Tag(name = "Maker — Inventário de Filamentos e Resinas", description = "Controle de rolos de filamento, frascos de resina e movimentações auditáveis de estoque")
public class MakerFilamentController {
    private final MakerFilamentService makerFilamentService;

    @Operation(
            summary = "Listar inventário de filamentos e resinas",
            description = "Retorna página com os rolos de filamento e frascos de resina cadastrados no estoque do Maker."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inventário retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<FilamentResponse>>> listFilaments(
            @Parameter(description = "Busca textual por nome, marca ou cor")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filtrar por tipo de material")
            @RequestParam(required = false) MaterialType material,
            @Parameter(description = "Filtrar apenas itens com estoque abaixo do mínimo")
            @RequestParam(required = false) Boolean lowStockOnly,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerFilamentService.listFilaments(q, material, lowStockOnly, pageable));
    }

    @Operation(
            summary = "Cadastrar novo filamento ou resina",
            description = "Adiciona um novo rolo de filamento ou frasco de resina ao inventário do estúdio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Item cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<FilamentResponse>> createFilament(
            @Valid @RequestBody CreateFilamentRequest request) {
        return BaseResponse.created(makerFilamentService.createFilament(request));
    }

    @Operation(
            summary = "Atualizar dados do filamento ou resina (PATCH)",
            description = "Atualiza parcialmente propriedades de identificação, custo, localização ou saldo de um item do inventário."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Item pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Filamento não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{filamentGuid}")
    public ResponseEntity<BaseResponse<FilamentResponse>> updateFilament(
            @Parameter(description = "GUID do filamento/resina", required = true)
            @PathVariable String filamentGuid,
            @Valid @RequestBody UpdateFilamentRequest request) {
        return BaseResponse.ok(makerFilamentService.updateFilament(filamentGuid, request));
    }

    @Operation(
            summary = "Registrar movimentação de estoque",
            description = "Aplica um ajuste auditável positivo ou negativo em gramas no saldo do filamento, preservando histórico e impedindo saldo negativo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estoque ajustado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Ajuste inválido", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Item pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Filamento não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{filamentGuid}/stock-adjustments")
    public ResponseEntity<BaseResponse<FilamentResponse>> adjustStock(
            @Parameter(description = "GUID do filamento/resina", required = true)
            @PathVariable String filamentGuid,
            @Valid @RequestBody AdjustFilamentStockRequest request) {
        return BaseResponse.ok(makerFilamentService.adjustStock(filamentGuid, request));
    }

    @Operation(
            summary = "Remover filamento do inventário",
            description = "Exclui logicamente um item do inventário e desvincula referências em impressoras e slots AMS."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Filamento removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Item pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Filamento não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{filamentGuid}")
    public ResponseEntity<Void> deleteFilament(
            @Parameter(description = "GUID do filamento/resina", required = true)
            @PathVariable String filamentGuid) {
        makerFilamentService.deleteFilament(filamentGuid);
        return ResponseEntity.noContent().build();
    }
}
