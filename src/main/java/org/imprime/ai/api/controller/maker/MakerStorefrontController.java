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
import org.imprime.ai.api.http.request.maker.CreateMakerModelRequest;
import org.imprime.ai.api.http.request.maker.CreateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.CreatePortfolioItemRequest;
import org.imprime.ai.api.http.request.maker.ReorderMakerItemsRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.UpdatePortfolioItemRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerModelResponse;
import org.imprime.ai.api.http.response.maker.MakerProductResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.service.MakerModelService;
import org.imprime.ai.api.service.MakerStorefrontService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker")
@RequireMaker
@Tag(name = "Maker — Vitrine e Portfólio", description = "Gerenciamento autenticado do portfólio e produtos da loja do Maker (empresa extraída do JWT)")
public class MakerStorefrontController {
    private final MakerStorefrontService makerStorefrontService;
    private final MakerModelService makerModelService;

    @PutMapping("/portfolio/order")
    public ResponseEntity<Void> reorderPortfolio(@Valid @RequestBody ReorderMakerItemsRequest request) {
        makerStorefrontService.reorderPortfolio(request.itemGuids());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/products/order")
    public ResponseEntity<Void> reorderProducts(@Valid @RequestBody ReorderMakerItemsRequest request) {
        makerStorefrontService.reorderProducts(request.itemGuids());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/models/order")
    public ResponseEntity<Void> reorderModels(@Valid @RequestBody ReorderMakerItemsRequest request) {
        makerModelService.reorderModels(request.itemGuids());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar itens do próprio portfólio",
            description = "Retorna página com os projetos publicados no portfólio do estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfólio retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/portfolio")
    public ResponseEntity<BaseResponse<PageResponse<PortfolioItemResponse>>> listOwnPortfolio(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerStorefrontService.listOwnPortfolio(pageable));
    }

    @Operation(
            summary = "Adicionar projeto ao portfólio do Maker",
            description = "Publica um novo projeto realizado no portfólio do estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Item adicionado ao portfólio"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/portfolio")
    public ResponseEntity<BaseResponse<PortfolioItemResponse>> createPortfolioItem(
            @Valid @RequestBody CreatePortfolioItemRequest request) {
        return BaseResponse.created(makerStorefrontService.createPortfolioItem(request));
    }

    @Operation(
            summary = "Atualizar item do portfólio",
            description = "Atualiza parcialmente um projeto publicado no portfólio do estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é Maker ou item pertence a outro estúdio", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item de portfólio não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/portfolio/{itemGuid}")
    public ResponseEntity<BaseResponse<PortfolioItemResponse>> updatePortfolioItem(
            @Parameter(description = "GUID do item de portfólio", required = true)
            @PathVariable String itemGuid,
            @Valid @RequestBody UpdatePortfolioItemRequest request) {
        return BaseResponse.ok(makerStorefrontService.updatePortfolioItem(itemGuid, request));
    }

    @Operation(
            summary = "Excluir item do portfólio",
            description = "Remove um item do portfólio do estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Item removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é Maker ou item pertence a outro estúdio", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item de portfólio não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/portfolio/{itemGuid}")
    public ResponseEntity<Void> deletePortfolioItem(
            @Parameter(description = "GUID do item de portfólio", required = true)
            @PathVariable String itemGuid) {
        makerStorefrontService.deletePortfolioItem(itemGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar produtos da própria loja",
            description = "Retorna página com os produtos de preço fixo cadastrados pelo estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produtos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/products")
    public ResponseEntity<BaseResponse<PageResponse<MakerProductResponse>>> listOwnProducts(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerStorefrontService.listOwnProducts(pageable));
    }

    @Operation(
            summary = "Cadastrar produto na loja do Maker",
            description = "Cria um novo produto de preço fixo na vitrine do estúdio autenticado e gera slug único para o catálogo global."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Produto cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do produto inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/products")
    public ResponseEntity<BaseResponse<MakerProductResponse>> createProduct(
            @Valid @RequestBody CreateMakerProductRequest request) {
        return BaseResponse.created(makerStorefrontService.createProduct(request));
    }

    @Operation(
            summary = "Atualizar produto da loja do Maker",
            description = "Atualiza parcialmente um produto pertencente ao estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é Maker ou produto pertence a outro estúdio", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/products/{productGuid}")
    public ResponseEntity<BaseResponse<MakerProductResponse>> updateProduct(
            @Parameter(description = "GUID do produto", required = true)
            @PathVariable String productGuid,
            @Valid @RequestBody UpdateMakerProductRequest request) {
        return BaseResponse.ok(makerStorefrontService.updateProduct(productGuid, request));
    }

    @Operation(
            summary = "Excluir produto da loja do Maker",
            description = "Remove logicamente um produto da loja do estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Produto removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é Maker ou produto pertence a outro estúdio", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/products/{productGuid}")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "GUID do produto", required = true)
            @PathVariable String productGuid) {
        makerStorefrontService.deleteProduct(productGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar modelos 3D enviados pelo estúdio",
            description = "Retorna página com todos os modelos 3D enviados pelo estúdio autenticado (em análise, aprovados, recusados ou removidos)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Modelos 3D retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/models")
    public ResponseEntity<BaseResponse<PageResponse<MakerModelResponse>>> listOwnModels(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerModelService.listOwnModels(pageable));
    }

    @Operation(
            summary = "Solicitar envio de modelo 3D",
            description = "Recebe o arquivo 3D (.stl, .3mf, .obj, .glb, .scad) ou código OpenSCAD paramétrico em metadata.scadCode via multipart/form-data, publica na fila PENDING_REVIEW para moderação e retorna o modelo criado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Solicitação de envio registrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo ou metadados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping(value = "/models", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<MakerModelResponse>> submitModel(
            @Parameter(description = "Arquivo 3D (.stl, .3mf, .obj, .glb, .scad). Opcional quando customizable = true e metadata.scadCode é enviado.")
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Parameter(description = "Metadados técnicos e comerciais do modelo 3D", required = true)
            @RequestPart("metadata") @Valid CreateMakerModelRequest metadata) {
        return BaseResponse.created(makerModelService.submitModel(file, metadata));
    }

    @Operation(
            summary = "Excluir modelo 3D enviado pelo estúdio",
            description = "Remove logicamente um modelo 3D pertencente ao estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Modelo removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Modelo pertence a outro usuário/estúdio", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Modelo 3D não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/models/{modelGuid}")
    public ResponseEntity<Void> deleteOwnModel(
            @Parameter(description = "GUID do modelo 3D", required = true)
            @PathVariable String modelGuid) {
        makerModelService.deleteOwnModel(modelGuid);
        return ResponseEntity.noContent().build();
    }
}
