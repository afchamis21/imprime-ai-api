package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminMakerApproveRequest;
import org.imprime.ai.api.http.request.admin.AdminMakerStatusRequest;
import org.imprime.ai.api.http.request.admin.AdminModerationReasonRequest;
import org.imprime.ai.api.http.request.admin.AdminProductStatusRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminCatalogProductResponse;
import org.imprime.ai.api.http.response.admin.AdminMakerDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminMakerSummaryResponse;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.service.AdminMakerCatalogService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@RequireAdmin
@Tag(name = "Admin — Makers e Catálogo", description = "Onboarding e aprovação de Makers (impressoras e materiais) e moderação de catálogo")
public class AdminMakerCatalogController {
    private final AdminMakerCatalogService makerCatalogService;

    @Operation(summary = "Listar Makers e fila de aprovação")
    @GetMapping("/makers")
    public ResponseEntity<BaseResponse<PageResponse<AdminMakerSummaryResponse>>> listMakers(
            @RequestParam(required = false) MakerApprovalStatus approvalStatus,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) Boolean online,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(makerCatalogService.listMakers(approvalStatus, verified, online, q, pageable));
    }

    @Operation(summary = "Obter perfil completo de um Maker (impressoras, materiais e produtos)")
    @GetMapping("/makers/{companyGuid}")
    public ResponseEntity<BaseResponse<AdminMakerDetailResponse>> getMakerDetail(@PathVariable String companyGuid) {
        return BaseResponse.ok(makerCatalogService.getMakerDetail(companyGuid));
    }

    @Operation(summary = "Aprovar novo Maker")
    @PostMapping("/makers/{companyGuid}/approve")
    public ResponseEntity<BaseResponse<AdminMakerDetailResponse>> approveMaker(
            @PathVariable String companyGuid,
            @Valid @RequestBody(required = false) AdminMakerApproveRequest request
    ) {
        return BaseResponse.ok(makerCatalogService.approveMaker(companyGuid, request));
    }

    @Operation(summary = "Recusar solicitação de Maker (motivo obrigatório)")
    @PostMapping("/makers/{companyGuid}/reject")
    public ResponseEntity<BaseResponse<AdminMakerDetailResponse>> rejectMaker(
            @PathVariable String companyGuid,
            @Valid @RequestBody AdminModerationReasonRequest request
    ) {
        return BaseResponse.ok(makerCatalogService.rejectMaker(companyGuid, request));
    }

    @Operation(summary = "Atualizar status, aprovação ou selo verificado do Maker")
    @PatchMapping("/makers/{companyGuid}/status")
    public ResponseEntity<BaseResponse<AdminMakerDetailResponse>> updateMakerStatus(
            @PathVariable String companyGuid,
            @Valid @RequestBody AdminMakerStatusRequest request
    ) {
        return BaseResponse.ok(makerCatalogService.updateMakerStatus(companyGuid, request));
    }

    @Operation(summary = "Listar todos os produtos e serviços do catálogo")
    @GetMapping("/catalog/products")
    public ResponseEntity<BaseResponse<PageResponse<AdminCatalogProductResponse>>> listCatalogProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String makerGuid,
            @RequestParam(required = false) StatusCd status,
            @RequestParam(required = false) Boolean inStock,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(
                makerCatalogService.listCatalogProducts(q, category, makerGuid, status, inStock, pageable)
        );
    }

    @Operation(summary = "Ativar, desativar ou moderar produto do catálogo")
    @PatchMapping("/catalog/products/{productGuid}/status")
    public ResponseEntity<BaseResponse<AdminCatalogProductResponse>> updateProductStatus(
            @PathVariable String productGuid,
            @Valid @RequestBody AdminProductStatusRequest request
    ) {
        return BaseResponse.ok(makerCatalogService.updateProductStatus(productGuid, request));
    }
}
