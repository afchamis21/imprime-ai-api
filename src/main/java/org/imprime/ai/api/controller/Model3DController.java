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
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.auth.OptionalAuth;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.common.CreateReviewReplyRequest;
import org.imprime.ai.api.http.request.model3d.Model3DCatalogFilter;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.http.response.common.ReviewLikeResponse;
import org.imprime.ai.api.http.response.common.ReviewReplyResponse;
import org.imprime.ai.api.http.response.model3d.Model3DCatalogItemResponse;
import org.imprime.ai.api.http.response.model3d.Model3DDetailResponse;
import org.imprime.ai.api.http.response.model3d.ModelCustomizationResponse;
import org.imprime.ai.api.model.enums.ModelPricingFilter;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.service.Model3DService;
import org.imprime.ai.api.service.OrderManagementService;
import org.imprime.ai.api.service.ReviewService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/models")
@Tag(name = "Modelos 3D", description = "Catálogo de modelos 3D digitais, avaliações, favoritos e customização paramétrica OpenSCAD")
public class Model3DController {
    private final Model3DService model3DService;
    private final ReviewService reviewService;

    @OptionalAuth
    @Operation(
            summary = "Listar modelos 3D do catálogo",
            description = "Retorna página de modelos 3D digitais gratuitos ou pagos com suporte a filtros por categoria, faixa de preço e tags. Autenticação JWT opcional (preenche `favorited`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Modelos 3D retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de filtro inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido ou expirado quando enviado no header", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<Model3DCatalogItemResponse>>> listModels(
            @Parameter(description = "Busca textual em nome e autor")
            @RequestParam(required = false) String q,
            @Parameter(description = "Categoria do modelo 3D")
            @RequestParam(required = false) String category,
            @Parameter(description = "Filtro de precificação (`ALL`, `FREE`, `PAID`)")
            @RequestParam(required = false) ModelPricingFilter pricing,
            @Parameter(description = "Tags técnicas/materiais")
            @RequestParam(required = false) List<String> tags,
            @Parameter(description = "Filtrar apenas modelos com suporte à customização OpenSCAD")
            @RequestParam(required = false) Boolean customizableOnly,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Model3DCatalogFilter filter = new Model3DCatalogFilter(q, category, pricing, tags, customizableOnly);
        return BaseResponse.ok(model3DService.listModels(filter, pageable));
    }

    @OptionalAuth
    @Operation(
            summary = "Obter detalhes de um modelo 3D",
            description = "Retorna detalhes completos de um modelo 3D por `slug` ou `guid`, incluindo URL do arquivo para visualização 3D. Autenticação JWT opcional (preenche `favorited`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Modelo 3D encontrado"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido ou expirado quando enviado no header", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Modelo 3D não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}")
    public ResponseEntity<BaseResponse<Model3DDetailResponse>> getModel(
            @Parameter(description = "Slug amigável ou GUID do modelo 3D", required = true)
            @PathVariable String slugOrGuid) {
        return BaseResponse.ok(model3DService.getModelDetail(slugOrGuid));
    }

    @OptionalAuth
    @Operation(
            summary = "Obter binário do arquivo 3D do modelo",
            description = "Faz streaming do conteúdo binário do arquivo 3D de um modelo aprovado (ou do próprio autor quando em análise) para renderização no visualizador 3D."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arquivo 3D retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Modelo ou arquivo 3D não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}/file")
    public ResponseEntity<byte[]> getModelFile(
            @Parameter(description = "Slug amigável ou GUID do modelo 3D", required = true)
            @PathVariable String slugOrGuid,
            @RequestParam(defaultValue = "false") boolean download) {
        OrderManagementService.OrderFileContent content = model3DService.downloadModelFile(slugOrGuid);

        ContentDisposition disposition = ContentDisposition
                .builder(download ? "attachment" : "inline")
                .filename(content.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .body(content.bytes());
    }

    @JwtAuthenticated
    @Operation(
            summary = "Favoritar modelo 3D",
            description = "Adiciona o modelo 3D aos favoritos do usuário autenticado de forma idempotente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Modelo favoritado"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Modelo 3D não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/{modelGuid}/favorite")
    public ResponseEntity<BaseResponse<FavoriteStatusResponse>> favoriteModel(
            @Parameter(description = "GUID do modelo 3D", required = true)
            @PathVariable String modelGuid) {
        return BaseResponse.ok(model3DService.addFavorite(modelGuid));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Remover modelo 3D dos favoritos",
            description = "Remove o modelo 3D dos favoritos do usuário autenticado de forma idempotente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Modelo removido dos favoritos"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Modelo 3D não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{modelGuid}/favorite")
    public ResponseEntity<Void> unfavoriteModel(
            @Parameter(description = "GUID do modelo 3D", required = true)
            @PathVariable String modelGuid) {
        model3DService.removeFavorite(modelGuid);
        return ResponseEntity.noContent().build();
    }

    @OptionalAuth
    @SecurityRequirements
    @Operation(
            summary = "Listar avaliações de um modelo 3D",
            description = "Retorna página de avaliações sobre a qualidade e imprimibilidade do modelo 3D."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avaliações retornadas com sucesso"),
            @ApiResponse(responseCode = "404", description = "Modelo 3D não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{modelGuid}/reviews")
    public ResponseEntity<BaseResponse<PageResponse<ReviewResponse>>> listModelReviews(
            @Parameter(description = "GUID ou slug do modelo 3D", required = true)
            @PathVariable String modelGuid,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return BaseResponse.ok(reviewService.listReviews(ReviewTargetType.MODEL_3D, modelGuid, pageable));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Publicar avaliação em um modelo 3D",
            description = "Cria uma nova avaliação para o modelo 3D utilizando o usuário autenticado no token JWT."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Avaliação publicada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de avaliação inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Modelo 3D não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Usuário já avaliou este modelo", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{modelGuid}/reviews")
    public ResponseEntity<BaseResponse<ReviewResponse>> createModelReview(
            @Parameter(description = "GUID do modelo 3D", required = true)
            @PathVariable String modelGuid,
            @Valid @RequestBody CreateReviewRequest request) {
        return BaseResponse.created(reviewService.createReview(ReviewTargetType.MODEL_3D, modelGuid, request));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Editar avaliação de modelo 3D",
            description = "Atualiza uma avaliação publicada pelo próprio usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avaliação atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de avaliação inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o autor da avaliação", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Modelo 3D ou avaliação não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/{modelGuid}/reviews/{reviewGuid}")
    public ResponseEntity<BaseResponse<ReviewResponse>> updateModelReview(
            @Parameter(description = "GUID do modelo 3D", required = true)
            @PathVariable String modelGuid,
            @Parameter(description = "GUID da avaliação", required = true)
            @PathVariable String reviewGuid,
            @Valid @RequestBody CreateReviewRequest request) {
        return BaseResponse.ok(reviewService.updateReview(ReviewTargetType.MODEL_3D, modelGuid, reviewGuid, request));
    }

    @JwtAuthenticated
    @Operation(
            summary = "Excluir avaliação de modelo 3D",
            description = "Remove uma avaliação publicada pelo próprio usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Avaliação excluída com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o autor da avaliação", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Modelo 3D ou avaliação não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{modelGuid}/reviews/{reviewGuid}")
    public ResponseEntity<Void> deleteModelReview(
            @Parameter(description = "GUID do modelo 3D", required = true)
            @PathVariable String modelGuid,
            @Parameter(description = "GUID da avaliação", required = true)
            @PathVariable String reviewGuid) {
        reviewService.deleteReview(ReviewTargetType.MODEL_3D, modelGuid, reviewGuid);
        return ResponseEntity.noContent().build();
    }

    @JwtAuthenticated
    @PostMapping("/{modelGuid}/reviews/{reviewGuid}/replies")
    public ResponseEntity<BaseResponse<ReviewReplyResponse>> replyToReview(
            @PathVariable String modelGuid, @PathVariable String reviewGuid,
            @Valid @RequestBody CreateReviewReplyRequest request) {
        return BaseResponse.created(reviewService.reply(ReviewTargetType.MODEL_3D, modelGuid, reviewGuid, request));
    }

    @JwtAuthenticated
    @PutMapping("/{modelGuid}/reviews/{reviewGuid}/like")
    public ResponseEntity<BaseResponse<ReviewLikeResponse>> likeReview(
            @PathVariable String modelGuid, @PathVariable String reviewGuid) {
        return BaseResponse.ok(reviewService.setLiked(ReviewTargetType.MODEL_3D, modelGuid, reviewGuid, true));
    }

    @JwtAuthenticated
    @DeleteMapping("/{modelGuid}/reviews/{reviewGuid}/like")
    public ResponseEntity<BaseResponse<ReviewLikeResponse>> unlikeReview(
            @PathVariable String modelGuid, @PathVariable String reviewGuid) {
        return BaseResponse.ok(reviewService.setLiked(ReviewTargetType.MODEL_3D, modelGuid, reviewGuid, false));
    }

    @OptionalAuth
    @Operation(
            summary = "Obter definição de customização OpenSCAD",
            description = "Retorna o script OpenSCAD paramétrico e o esquema dinâmico de parâmetros para renderização e compilação no navegador. Autenticação JWT opcional."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados de customização retornados com sucesso"),
            @ApiResponse(responseCode = "404", description = "Modelo 3D não encontrado ou não customizável", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{slugOrGuid}/customization")
    public ResponseEntity<BaseResponse<ModelCustomizationResponse>> getCustomization(
            @Parameter(description = "Slug amigável ou GUID do modelo 3D", required = true)
            @PathVariable String slugOrGuid) {
        return BaseResponse.ok(model3DService.getCustomization(slugOrGuid));
    }
}
