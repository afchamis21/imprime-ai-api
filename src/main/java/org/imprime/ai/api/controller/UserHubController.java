package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.user.UpdateUserProfileRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.user.ModelDownloadResponse;
import org.imprime.ai.api.http.response.user.PurchasedModelResponse;
import org.imprime.ai.api.http.response.user.UserFavoriteResponse;
import org.imprime.ai.api.http.response.user.UserReviewResponse;
import org.imprime.ai.api.model.dto.FullUserDTO;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.service.FavoriteService;
import org.imprime.ai.api.service.UserHubService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me")
@JwtAuthenticated
@Tag(name = "Central do Usuário", description = "Perfil pessoal, pedidos do comprador, biblioteca de modelos adquiridos, favoritos e avaliações enviadas")
public class UserHubController {
    private final UserHubService userHubService;
    private final FavoriteService favoriteService;

    @Operation(
            summary = "Atualizar dados cadastrais do perfil",
            description = "Atualiza nome, e-mail e telefone do usuário autenticado. Alterações de endereço utilizam `/users/me/addresses`."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou e-mail/telefone já em uso", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/profile")
    public ResponseEntity<BaseResponse<FullUserDTO>> updateProfile(
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return BaseResponse.ok(userHubService.updateProfile(request));
    }

    @Operation(
            summary = "Listar pedidos realizados pelo usuário",
            description = "Retorna página com os pedidos do comprador autenticado, com filtro opcional por status e busca textual."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedidos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/orders")
    public ResponseEntity<BaseResponse<PageResponse<OrderSummaryResponse>>> listUserOrders(
            @Parameter(description = "Filtrar por status do pedido")
            @RequestParam(required = false) OrderStatusCd status,
            @Parameter(description = "Busca por código do pedido ou nome do item/Maker")
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(userHubService.listUserOrders(status, q, pageable));
    }

    @Operation(
            summary = "Listar modelos 3D adquiridos pelo usuário",
            description = "Retorna página com os modelos digitais comprados na biblioteca pessoal do usuário."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Biblioteca retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/purchased-models")
    public ResponseEntity<BaseResponse<PageResponse<PurchasedModelResponse>>> listPurchasedModels(
            @Parameter(description = "Busca por título ou designer")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filtrar por formato do arquivo 3D")
            @RequestParam(required = false) FileFormat3D format,
            @Parameter(description = "Filtrar apenas modelos customizáveis")
            @RequestParam(required = false) Boolean customizableOnly,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(userHubService.listPurchasedModels(q, format, customizableOnly, pageable));
    }

    @Operation(
            summary = "Gerar URL de download de modelo 3D comprado",
            description = "Registra auditoria de download, incrementa o contador e retorna uma URL pré-assinada temporária no Object Storage para o comprador baixar o arquivo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL de download gerada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não adquiriu este modelo", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Compra ou modelo não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/purchased-models/{purchaseGuid}/download-url")
    public ResponseEntity<BaseResponse<ModelDownloadResponse>> generateDownloadUrl(
            @Parameter(description = "GUID do registro de compra do modelo", required = true)
            @PathVariable String purchaseGuid) {
        return BaseResponse.ok(userHubService.generatePurchasedModelDownloadUrl(purchaseGuid));
    }

    @Operation(
            summary = "Listar favoritos do usuário",
            description = "Retorna página com Makers, produtos e modelos 3D salvos nos favoritos do usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Favoritos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/favorites")
    public ResponseEntity<BaseResponse<PageResponse<UserFavoriteResponse>>> listFavorites(
            @Parameter(description = "Filtrar por tipo de favorito")
            @RequestParam(required = false) FavoriteTargetType type,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(favoriteService.listUserFavorites(type, pageable));
    }

    @Operation(
            summary = "Remover item da lista de favoritos",
            description = "Remove um registro de favorito pertencente ao usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Favorito removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Favorito pertence a outro usuário", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Favorito não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/favorites/{favoriteGuid}")
    public ResponseEntity<Void> removeFavorite(
            @Parameter(description = "GUID do registro de favorito", required = true)
            @PathVariable String favoriteGuid) {
        favoriteService.removeFavorite(favoriteGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar avaliações enviadas pelo usuário",
            description = "Retorna página com o histórico de avaliações publicadas pelo usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avaliações retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/reviews")
    public ResponseEntity<BaseResponse<PageResponse<UserReviewResponse>>> listUserReviews(
            @Parameter(description = "Filtrar por tipo de alvo avaliado")
            @RequestParam(required = false) ReviewTargetType targetType,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(userHubService.listUserReviews(targetType, pageable));
    }

    @Operation(
            summary = "Editar avaliação enviada pelo usuário",
            description = "Atualiza a nota e o comentário de uma avaliação publicada pelo usuário autenticado e recalcula a média do alvo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avaliação atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o autor da avaliação", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Avaliação não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/reviews/{reviewGuid}")
    public ResponseEntity<BaseResponse<UserReviewResponse>> updateUserReview(
            @Parameter(description = "GUID da avaliação", required = true)
            @PathVariable String reviewGuid,
            @Valid @RequestBody CreateReviewRequest request) {
        return BaseResponse.ok(userHubService.updateUserReview(reviewGuid, request));
    }

    @Operation(
            summary = "Excluir avaliação enviada pelo usuário",
            description = "Remove uma avaliação publicada pelo usuário autenticado e recalcula a média do alvo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Avaliação excluída com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o autor da avaliação", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Avaliação não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/reviews/{reviewGuid}")
    public ResponseEntity<Void> deleteUserReview(
            @Parameter(description = "GUID da avaliação", required = true)
            @PathVariable String reviewGuid) {
        userHubService.deleteUserReview(reviewGuid);
        return ResponseEntity.noContent().build();
    }
}
