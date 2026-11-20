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
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.auth.OptionalAuth;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.product.ProductCatalogFilter;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.imprime.ai.api.http.response.product.ProductDetailResponse;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.service.ProductService;
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
@RequestMapping("/products")
@Tag(name = "Produtos", description = "Catálogo público de produtos impressos, favoritos e avaliações")
public class ProductController {
    private final ProductService productService;
    private final ReviewService reviewService;

    @OptionalAuth
    @Operation(
            summary = "Listar produtos do marketplace",
            description = "Retorna página de produtos disponíveis com filtros combináveis e ordenação server-side. Autenticação JWT opcional (preenche `favorited`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produtos retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Filtros inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido ou expirado quando enviado no header", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<ProductCatalogItemResponse>>> listProducts(
            @Parameter(description = "Busca textual em nome do produto e nome do Maker")
            @RequestParam(required = false) String q,
            @Parameter(description = "Categoria do produto")
            @RequestParam(required = false) String category,
            @Parameter(description = "GUID do Maker vendedor")
            @RequestParam(required = false) String makerGuid,
            @Parameter(description = "Preço mínimo em BRL")
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @Parameter(description = "Preço máximo em BRL")
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
            @Parameter(description = "Nota média mínima")
            @RequestParam(required = false) BigDecimal minRating,
            @Parameter(description = "Filtro multi-seleção de materiais")
            @RequestParam(required = false) List<MaterialType> materials,
            @Parameter(description = "Filtrar apenas produtos disponíveis em estoque")
            @RequestParam(required = false) Boolean inStockOnly,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        ProductCatalogFilter filter = new ProductCatalogFilter(
                q, category, makerGuid, minPrice, maxPrice, minRating, materials, inStockOnly
        );
        return BaseResponse.ok(productService.listProducts(filter, pageable));
    }

    @OptionalAuth
    @Operation(
            summary = "Obter detalhes de um produto",
            description = "Busca os detalhes completos de um produto por `slug` ou `guid`. Autenticação JWT opcional (preenche `favorited`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto encontrado"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido ou expirado quando enviado no header", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}")
    public ResponseEntity<BaseResponse<ProductDetailResponse>> getProduct(
            @Parameter(description = "Slug amigável ou GUID do produto", required = true)
            @PathVariable String slugOrGuid) {
        return BaseResponse.ok(productService.getProductDetail(slugOrGuid));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Favoritar produto",
            description = "Marca um produto como favorito para o usuário autenticado de forma idempotente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto favoritado"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/{productGuid}/favorite")
    public ResponseEntity<BaseResponse<FavoriteStatusResponse>> favoriteProduct(
            @Parameter(description = "GUID do produto", required = true)
            @PathVariable String productGuid) {
        return BaseResponse.ok(productService.addFavorite(productGuid));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Remover produto dos favoritos",
            description = "Remove o produto dos favoritos do usuário autenticado de forma idempotente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Produto removido dos favoritos"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{productGuid}/favorite")
    public ResponseEntity<Void> unfavoriteProduct(
            @Parameter(description = "GUID do produto", required = true)
            @PathVariable String productGuid) {
        productService.removeFavorite(productGuid);
        return ResponseEntity.noContent().build();
    }

    @SkipAuth
    @SecurityRequirements
    @Operation(
            summary = "Listar avaliações de um produto",
            description = "Retorna página de avaliações publicadas para o produto informado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avaliações retornadas com sucesso"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{productGuid}/reviews")
    public ResponseEntity<BaseResponse<PageResponse<ReviewResponse>>> listProductReviews(
            @Parameter(description = "GUID ou slug do produto", required = true)
            @PathVariable String productGuid,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return BaseResponse.ok(reviewService.listReviews(ReviewTargetType.PRODUCT, productGuid, pageable));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Publicar avaliação em um produto",
            description = "Cria uma nova avaliação para o produto usando os dados do usuário autenticado no token JWT."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Avaliação publicada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de avaliação inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Usuário já avaliou este produto", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{productGuid}/reviews")
    public ResponseEntity<BaseResponse<ReviewResponse>> createProductReview(
            @Parameter(description = "GUID do produto", required = true)
            @PathVariable String productGuid,
            @Valid @RequestBody CreateReviewRequest request) {
        return BaseResponse.created(reviewService.createReview(ReviewTargetType.PRODUCT, productGuid, request));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Editar avaliação de produto",
            description = "Atualiza uma avaliação publicada pelo próprio usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avaliação atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de avaliação inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o autor da avaliação", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto ou avaliação não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/{productGuid}/reviews/{reviewGuid}")
    public ResponseEntity<BaseResponse<ReviewResponse>> updateProductReview(
            @Parameter(description = "GUID do produto", required = true)
            @PathVariable String productGuid,
            @Parameter(description = "GUID da avaliação", required = true)
            @PathVariable String reviewGuid,
            @Valid @RequestBody CreateReviewRequest request) {
        return BaseResponse.ok(reviewService.updateReview(ReviewTargetType.PRODUCT, productGuid, reviewGuid, request));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Excluir avaliação de produto",
            description = "Remove uma avaliação publicada pelo próprio usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Avaliação excluída com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o autor da avaliação", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto ou avaliação não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{productGuid}/reviews/{reviewGuid}")
    public ResponseEntity<Void> deleteProductReview(
            @Parameter(description = "GUID do produto", required = true)
            @PathVariable String productGuid,
            @Parameter(description = "GUID da avaliação", required = true)
            @PathVariable String reviewGuid) {
        reviewService.deleteReview(ReviewTargetType.PRODUCT, productGuid, reviewGuid);
        return ResponseEntity.noContent().build();
    }
}
