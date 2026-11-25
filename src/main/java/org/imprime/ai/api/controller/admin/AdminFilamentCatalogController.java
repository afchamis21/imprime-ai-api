package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminOptionalNoteRequest;
import org.imprime.ai.api.http.request.admin.PatchFilamentCatalogRequest;
import org.imprime.ai.api.http.request.admin.UpsertFilamentCatalogRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.FilamentCatalogResponse;
import org.imprime.ai.api.model.enums.FilamentFinishType;
import org.imprime.ai.api.model.enums.HygroscopicLevel;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.service.FilamentCatalogService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "Admin - Catálogo de Filamentos", description = "CRUD administrativo do catálogo global de perfis de filamentos")
@RestController
@RequestMapping("/api/admin/catalog/filaments")
@RequiredArgsConstructor
@RequireAdmin
public class AdminFilamentCatalogController {
    private final FilamentCatalogService filamentCatalogService;

    @Operation(summary = "Listar perfis de filamentos do catálogo")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<FilamentCatalogResponse>>> listFilaments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) MaterialType material,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) FilamentFinishType finishType,
            @RequestParam(required = false) Boolean amsCompatible,
            @RequestParam(required = false) Boolean requiresEnclosure,
            @RequestParam(required = false) Boolean requiresHardenedNozzle,
            @RequestParam(required = false) HygroscopicLevel hygroscopicLevel,
            @RequestParam(required = false) BigDecimal diameterMm,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable
    ) {
        return BaseResponse.ok(filamentCatalogService.listFilaments(
                query, material, brand, finishType, amsCompatible,
                requiresEnclosure, requiresHardenedNozzle, hygroscopicLevel, diameterMm, includeInactive, pageable
        ));
    }

    @Operation(summary = "Obter perfil de filamento do catálogo")
    @GetMapping("/{guid}")
    public ResponseEntity<BaseResponse<FilamentCatalogResponse>> getFilament(@PathVariable String guid) {
        return BaseResponse.ok(filamentCatalogService.getFilamentByGuid(guid, true));
    }

    @Operation(summary = "Cadastrar novo perfil de filamento no catálogo")
    @PostMapping
    public ResponseEntity<BaseResponse<FilamentCatalogResponse>> createFilament(
            @Valid @RequestBody UpsertFilamentCatalogRequest request
    ) {
        return BaseResponse.created(filamentCatalogService.createFilament(request));
    }

    @Operation(summary = "Atualizar completamente um perfil de filamento")
    @PutMapping("/{guid}")
    public ResponseEntity<BaseResponse<FilamentCatalogResponse>> updateFilament(
            @PathVariable String guid,
            @Valid @RequestBody UpsertFilamentCatalogRequest request
    ) {
        return BaseResponse.ok(filamentCatalogService.updateFilament(guid, request));
    }

    @Operation(summary = "Edição rápida (parcial) de um perfil de filamento")
    @PatchMapping("/{guid}")
    public ResponseEntity<BaseResponse<FilamentCatalogResponse>> patchFilament(
            @PathVariable String guid,
            @Valid @RequestBody PatchFilamentCatalogRequest request
    ) {
        return BaseResponse.ok(filamentCatalogService.patchFilament(guid, request));
    }

    @Operation(summary = "Remover logicamente um perfil de filamento")
    @DeleteMapping("/{guid}")
    public ResponseEntity<Void> deleteFilament(
            @PathVariable String guid,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) AdminOptionalNoteRequest body
    ) {
        filamentCatalogService.deleteFilament(guid, CatalogDtoMapper.firstNonBlank(reason, body != null ? body.notes() : null));
        return ResponseEntity.noContent().build();
    }
}
