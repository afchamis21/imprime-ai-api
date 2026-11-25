package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.PatchPrinterCatalogRequest;
import org.imprime.ai.api.http.request.admin.UpsertPrinterCatalogRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.PrinterCatalogBriefDto;
import org.imprime.ai.api.http.response.catalog.PrinterCatalogResponse;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public interface PrinterCatalogService {
    PageResponse<PrinterCatalogResponse> listPrinters(
            String query,
            String brand,
            PrinterTechnology technology,
            Boolean isEnclosed,
            Boolean supportsMulticolor,
            MaterialType material,
            BigDecimal minVolumeX,
            BigDecimal minVolumeY,
            BigDecimal minVolumeZ,
            boolean includeInactive,
            Pageable pageable
    );

    PrinterCatalogResponse getPrinterByGuid(String guid, boolean includeInactive);

    PrinterCatalogResponse createPrinter(UpsertPrinterCatalogRequest request);

    PrinterCatalogResponse updatePrinter(String guid, UpsertPrinterCatalogRequest request);

    PrinterCatalogResponse patchPrinter(String guid, PatchPrinterCatalogRequest request);

    void deletePrinter(String guid, String reason);

    static PrinterCatalogBriefDto toBriefDto(PrinterCatalog entity) {
        if (entity == null) {
            return null;
        }
        List<MaterialType> materials = entity.getSupportedMaterials() == null
                ? List.of()
                : entity.getSupportedMaterials().stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        return new PrinterCatalogBriefDto(
                entity.getGuid(),
                entity.getSlug(),
                entity.getBrand(),
                entity.getModelName(),
                entity.getFullDisplayName(),
                entity.getBuildVolumeXMm(),
                entity.getBuildVolumeYMm(),
                entity.getBuildVolumeZMm(),
                Boolean.TRUE.equals(entity.getIsEnclosed()),
                Boolean.TRUE.equals(entity.getSupportsMulticolor()),
                entity.getMulticolorSystemName(),
                entity.getMaxColorSlots() != null ? entity.getMaxColorSlots() : 1,
                entity.getTechnology(),
                materials,
                entity.getImageUrl()
        );
    }
}
