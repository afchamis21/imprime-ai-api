package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.PatchFilamentCatalogRequest;
import org.imprime.ai.api.http.request.admin.UpsertFilamentCatalogRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.FilamentCatalogResponse;
import org.imprime.ai.api.model.enums.FilamentFinishType;
import org.imprime.ai.api.model.enums.HygroscopicLevel;
import org.imprime.ai.api.model.enums.MaterialType;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface FilamentCatalogService {
    PageResponse<FilamentCatalogResponse> listFilaments(
            String query,
            MaterialType material,
            String brand,
            FilamentFinishType finishType,
            Boolean amsCompatible,
            Boolean requiresEnclosure,
            Boolean requiresHardenedNozzle,
            HygroscopicLevel hygroscopicLevel,
            BigDecimal diameterMm,
            boolean includeInactive,
            Pageable pageable
    );

    FilamentCatalogResponse getFilamentByGuid(String guid, boolean includeInactive);

    FilamentCatalogResponse createFilament(UpsertFilamentCatalogRequest request);

    FilamentCatalogResponse updateFilament(String guid, UpsertFilamentCatalogRequest request);

    FilamentCatalogResponse patchFilament(String guid, PatchFilamentCatalogRequest request);

    void deleteFilament(String guid, String reason);
}
