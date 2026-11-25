package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.FilamentCatalogResponse;
import org.imprime.ai.api.http.response.catalog.PrinterCatalogResponse;
import org.imprime.ai.api.model.enums.FilamentFinishType;
import org.imprime.ai.api.model.enums.HygroscopicLevel;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.imprime.ai.api.service.FilamentCatalogService;
import org.imprime.ai.api.service.PrinterCatalogService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "Catálogo Público de Referência", description = "Consulta pública aos catálogos fixos de impressoras 3D e perfis de filamentos")
@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
@SkipAuth
public class CatalogReferenceController {
    private final PrinterCatalogService printerCatalogService;
    private final FilamentCatalogService filamentCatalogService;

    @Operation(summary = "Listar modelos ativos de impressoras 3D do catálogo")
    @GetMapping("/printers")
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
            @ParameterObject @PageableDefault(size = 100) Pageable pageable
    ) {
        return BaseResponse.ok(printerCatalogService.listPrinters(
                query, brand, technology, isEnclosed, supportsMulticolor,
                material, minVolumeX, minVolumeY, minVolumeZ, false, pageable
        ));
    }

    @Operation(summary = "Obter especificações técnicas de uma impressora 3D do catálogo")
    @GetMapping("/printers/{guid}")
    public ResponseEntity<BaseResponse<PrinterCatalogResponse>> getPrinter(@PathVariable String guid) {
        return BaseResponse.ok(printerCatalogService.getPrinterByGuid(guid, false));
    }

    @Operation(summary = "Listar perfis ativos de filamentos do catálogo")
    @GetMapping("/filaments")
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
            @ParameterObject @PageableDefault(size = 100) Pageable pageable
    ) {
        return BaseResponse.ok(filamentCatalogService.listFilaments(
                query, material, brand, finishType, amsCompatible,
                requiresEnclosure, requiresHardenedNozzle, hygroscopicLevel, diameterMm, false, pageable
        ));
    }

    @Operation(summary = "Obter perfil técnico de um filamento do catálogo")
    @GetMapping("/filaments/{guid}")
    public ResponseEntity<BaseResponse<FilamentCatalogResponse>> getFilament(@PathVariable String guid) {
        return BaseResponse.ok(filamentCatalogService.getFilamentByGuid(guid, false));
    }
}
