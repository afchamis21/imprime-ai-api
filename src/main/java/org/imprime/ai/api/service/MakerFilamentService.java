package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.AdjustFilamentStockRequest;
import org.imprime.ai.api.http.request.maker.CreateFilamentRequest;
import org.imprime.ai.api.http.request.maker.UpdateFilamentRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.FilamentResponse;
import org.imprime.ai.api.model.enums.MaterialType;
import org.springframework.data.domain.Pageable;

public interface MakerFilamentService {
    PageResponse<FilamentResponse> listFilaments(String query, MaterialType material, Boolean lowStockOnly, Pageable pageable);

    FilamentResponse createFilament(CreateFilamentRequest request);

    FilamentResponse updateFilament(String filamentGuid, UpdateFilamentRequest request);

    FilamentResponse adjustStock(String filamentGuid, AdjustFilamentStockRequest request);

    void deleteFilament(String filamentGuid);
}
