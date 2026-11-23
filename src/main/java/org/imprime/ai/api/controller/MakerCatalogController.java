package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.auth.OptionalAuth;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.common.CreateMakerReviewRequest;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.maker.MakerCatalogFilter;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.http.response.maker.MakerCatalogItemResponse;
import org.imprime.ai.api.http.response.maker.MakerModelResponse;
import org.imprime.ai.api.http.response.maker.MakerProfileResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.service.MakerCatalogService;
import org.imprime.ai.api.service.MakerModelService;
import org.imprime.ai.api.service.ReviewService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/makers")
@Tag(name = "Makers (Catálogo Público)", description = "Consulta pública de Makers, perfil de estúdio, portfólio, loja e favoritos")
public class MakerCatalogController {
    private final MakerCatalogService makerCatalogService;
    private final MakerModelService makerModelService;
    private final ReviewService reviewService;

    @OptionalAuth
    @Operation(
            summary = "Listar Makers do marketplace",
            description = "Retorna página de Makers disponíveis no catálogo público com filtros por especialidade, distância, nota, prazo, preço, disponibilidade e materiais. Autenticação JWT opcional (preenche o campo `favorited` quando enviada)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página de Makers retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de filtro inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido ou expirado quando enviado no header", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<MakerCatalogItemResponse>>> listMakers(
            @Parameter(description = "Busca textual em nome, especialidade e cidade")
            @RequestParam(required = false) String q,
            @Parameter(description = "Categoria de especialidade do Maker")
            @RequestParam(required = false) String specialty,
            @Parameter(description = "Distância máxima em quilômetros")
            @RequestParam(required = false) @Positive Double maxDistanceKm,
            @Parameter(description = "Nota média mínima (0 a 5)")
            @RequestParam(required = false) BigDecimal minRating,
            @Parameter(description = "Prazo máximo de produção em dias")
            @RequestParam(required = false) @Positive Integer maxDeadlineDays,
            @Parameter(description = "Preço base máximo em BRL")
            @RequestParam(required = false) @Positive BigDecimal maxPrice,
            @Parameter(description = "Retornar apenas Makers online")
            @RequestParam(required = false) Boolean onlyOnline,
            @Parameter(description = "Filtro multi-seleção por materiais operados")
            @RequestParam(required = false) List<MaterialType> materials,
            @Parameter(description = "Latitude de referência para cálculo de distância")
            @RequestParam(required = false) Double latitude,
            @Parameter(description = "Longitude de referência para cálculo de distância")
            @RequestParam(required = false) Double longitude,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        MakerCatalogFilter filter = new MakerCatalogFilter(
                q, specialty, maxDistanceKm, minRating, maxDeadlineDays, maxPrice, onlyOnline, materials, latitude, longitude
        );
        return BaseResponse.ok(makerCatalogService.listMakers(filter, pageable));
    }

    @OptionalAuth
    @Operation(
            summary = "Obter perfil público de um Maker",
            description = "Busca o perfil público detalhado do Maker por `slug` ou `guid`. Autenticação JWT opcional (preenche `favorited` e `isOwner`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Perfil do Maker retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido ou expirado quando enviado no header", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Maker não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}")
    public ResponseEntity<BaseResponse<MakerProfileResponse>> getMakerProfile(
            @Parameter(description = "Slug amigável ou GUID do Maker", required = true)
            @PathVariable String slugOrGuid) {
        return BaseResponse.ok(makerCatalogService.getMakerProfile(slugOrGuid));
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            summary = "Listar portfólio público de um Maker",
            description = "Retorna página com os projetos publicados no portfólio do Maker informado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfólio retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Maker não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}/portfolio")
    public ResponseEntity<BaseResponse<PageResponse<PortfolioItemResponse>>> listMakerPortfolio(
            @Parameter(description = "Slug amigável ou GUID do Maker", required = true)
            @PathVariable String slugOrGuid,
            @ParameterObject @PageableDefault(size = 12) Pageable pageable) {
        return BaseResponse.ok(makerCatalogService.listMakerPortfolio(slugOrGuid, pageable));
    }

    @OptionalAuth
    @Operation(
            summary = "Listar produtos da loja de um Maker",
            description = "Retorna página dos produtos publicados na loja do Maker informado. Autenticação JWT opcional (preenche `favorited`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produtos do Maker retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido ou expirado quando enviado no header", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Maker não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}/products")
    public ResponseEntity<BaseResponse<PageResponse<ProductCatalogItemResponse>>> listMakerProducts(
            @Parameter(description = "Slug amigável ou GUID do Maker", required = true)
            @PathVariable String slugOrGuid,
            @ParameterObject @PageableDefault(size = 12) Pageable pageable) {
        return BaseResponse.ok(makerCatalogService.listMakerProducts(slugOrGuid, pageable));
    }

    @OptionalAuth
    @Operation(
            summary = "Listar modelos 3D públicos de um Maker",
            description = "Retorna página dos modelos 3D aprovados enviados pelo Maker informado. Autenticação JWT opcional (preenche `favorited`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Modelos 3D do Maker retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido ou expirado quando enviado no header", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Maker não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}/models")
    public ResponseEntity<BaseResponse<PageResponse<MakerModelResponse>>> listMakerModels(
            @Parameter(description = "Slug amigável ou GUID do Maker", required = true)
            @PathVariable String slugOrGuid,
            @ParameterObject @PageableDefault(size = 12) Pageable pageable) {
        return BaseResponse.ok(makerModelService.listPublicMakerModels(slugOrGuid, pageable));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Favoritar um Maker",
            description = "Marca o Maker como favorito para o usuário autenticado de forma idempotente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Maker marcado como favorito"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Maker não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/{makerGuid}/favorite")
    public ResponseEntity<BaseResponse<FavoriteStatusResponse>> favoriteMaker(
            @Parameter(description = "GUID do Maker", required = true)
            @PathVariable String makerGuid) {
        return BaseResponse.ok(makerCatalogService.addFavorite(makerGuid));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Remover Maker dos favoritos",
            description = "Remove o Maker dos favoritos do usuário autenticado de forma idempotente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Maker removido dos favoritos"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Maker não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{makerGuid}/favorite")
    public ResponseEntity<Void> unfavoriteMaker(
            @Parameter(description = "GUID do Maker", required = true)
            @PathVariable String makerGuid) {
        makerCatalogService.removeFavorite(makerGuid);
        return ResponseEntity.noContent().build();
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            summary = "Listar avaliações de um Maker",
            description = "Retorna página com as avaliações publicadas para o Maker informado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avaliações retornadas com sucesso"),
            @ApiResponse(responseCode = "404", description = "Maker não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}/reviews")
    public ResponseEntity<BaseResponse<PageResponse<ReviewResponse>>> listMakerReviews(
            @Parameter(description = "Slug amigável ou GUID do Maker", required = true)
            @PathVariable String slugOrGuid,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return BaseResponse.ok(reviewService.listReviews(ReviewTargetType.MAKER, slugOrGuid, pageable));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Publicar avaliação em um Maker",
            description = "Cria uma avaliação para o Maker vinculada a uma compra concluída e recebida pelo usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Avaliação publicada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Nenhuma compra concluída elegível ou dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Pedido informado não pertence ao usuário", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Maker ou pedido não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Todas as compras concluídas com este Maker já foram avaliadas", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{slugOrGuid}/reviews")
    public ResponseEntity<BaseResponse<ReviewResponse>> createMakerReview(
            @Parameter(description = "Slug amigável ou GUID do Maker", required = true)
            @PathVariable String slugOrGuid,
            @Valid @RequestBody CreateMakerReviewRequest request) {
        return BaseResponse.created(reviewService.createMakerReview(slugOrGuid, request));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Editar avaliação de um Maker",
            description = "Atualiza uma avaliação publicada pelo próprio usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avaliação atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de avaliação inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o autor da avaliação", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Maker ou avaliação não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/{slugOrGuid}/reviews/{reviewGuid}")
    public ResponseEntity<BaseResponse<ReviewResponse>> updateMakerReview(
            @Parameter(description = "Slug amigável ou GUID do Maker", required = true)
            @PathVariable String slugOrGuid,
            @Parameter(description = "GUID da avaliação", required = true)
            @PathVariable String reviewGuid,
            @Valid @RequestBody CreateReviewRequest request) {
        return BaseResponse.ok(reviewService.updateReview(ReviewTargetType.MAKER, slugOrGuid, reviewGuid, request));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Excluir avaliação de um Maker",
            description = "Remove uma avaliação publicada pelo próprio usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Avaliação excluída com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o autor da avaliação", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Maker ou avaliação não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{slugOrGuid}/reviews/{reviewGuid}")
    public ResponseEntity<Void> deleteMakerReview(
            @Parameter(description = "Slug amigável ou GUID do Maker", required = true)
            @PathVariable String slugOrGuid,
            @Parameter(description = "GUID da avaliação", required = true)
            @PathVariable String reviewGuid) {
        reviewService.deleteReview(ReviewTargetType.MAKER, slugOrGuid, reviewGuid);
        return ResponseEntity.noContent().build();
    }
}
