package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminOptionalNoteRequest;
import org.imprime.ai.api.http.request.admin.PatchPrinterCatalogRequest;
import org.imprime.ai.api.http.request.admin.UpsertPrinterCatalogRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.PrinterCatalogResponse;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.imprime.ai.api.service.PrinterCatalogService;
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

@Tag(name = "Admin - Catálogo de Impressoras 3D", description = "CRUD administrativo do catálogo fixo de impressoras 3D")
@RestController
@RequestMapping("/api/admin/catalog/printers")
@RequiredArgsConstructor
@RequireAdmin
public class AdminPrinterCatalogController {
    private final PrinterCatalogService printerCatalogService;

    @Operation(summary = "Listar modelos de impressoras 3D do catálogo")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<PrinterCatalogResponse>>> listPrinters(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) PrinterTechnology technology,
            @RequestParam(required = false) Boolean isEnclosed,
            @RequestParam(required = false) Boolean supportsMulticolor,
            @RequestParam(required = false) MaterialType material,
            @RequestParam(required = false) BigDecimal minVolumeX,
            @RequestParam(required = false) BigDecimal minVolumeY,
            @RequestParam(required = false) BigDecimal minVolumeZ,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable
    ) {
        return BaseResponse.ok(printerCatalogService.listPrinters(
                query, brand, technology, isEnclosed, supportsMulticolor,
                material, minVolumeX, minVolumeY, minVolumeZ, includeInactive, pageable
        ));
    }

    @Operation(summary = "Obter detalhes de uma impressora 3D do catálogo")
    @GetMapping("/{guid}")
    public ResponseEntity<BaseResponse<PrinterCatalogResponse>> getPrinter(@PathVariable String guid) {
        return BaseResponse.ok(printerCatalogService.getPrinterByGuid(guid, true));
    }

    @Operation(summary = "Cadastrar nova impressora 3D no catálogo")
    @PostMapping
    public ResponseEntity<BaseResponse<PrinterCatalogResponse>> createPrinter(
            @Valid @RequestBody UpsertPrinterCatalogRequest request
    ) {
        return BaseResponse.created(printerCatalogService.createPrinter(request));
    }

    @Operation(summary = "Atualizar completamente uma impressora 3D no catálogo")
    @PutMapping("/{guid}")
    public ResponseEntity<BaseResponse<PrinterCatalogResponse>> updatePrinter(
            @PathVariable String guid,
            @Valid @RequestBody UpsertPrinterCatalogRequest request
    ) {
        return BaseResponse.ok(printerCatalogService.updatePrinter(guid, request));
    }

    @Operation(summary = "Edição rápida (parcial) de uma impressora 3D no catálogo")
    @PatchMapping("/{guid}")
    public ResponseEntity<BaseResponse<PrinterCatalogResponse>> patchPrinter(
            @PathVariable String guid,
            @Valid @RequestBody PatchPrinterCatalogRequest request
    ) {
        return BaseResponse.ok(printerCatalogService.patchPrinter(guid, request));
    }

    @Operation(summary = "Remover logicamente uma impressora 3D do catálogo")
    @DeleteMapping("/{guid}")
    public ResponseEntity<Void> deletePrinter(
            @PathVariable String guid,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) AdminOptionalNoteRequest body
    ) {
        printerCatalogService.deletePrinter(guid, CatalogDtoMapper.firstNonBlank(reason, body != null ? body.notes() : null));
        return ResponseEntity.noContent().build();
    }
}
