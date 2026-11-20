package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.AdjustFilamentStockRequest;
import org.imprime.ai.api.http.request.maker.CreateFilamentRequest;
import org.imprime.ai.api.http.request.maker.UpdateFilamentRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.FilamentResponse;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerFilamentService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MakerFilamentServiceImpl implements MakerFilamentService {

    @Override
    public PageResponse<FilamentResponse> listFilaments(String query, MaterialType material, Boolean lowStockOnly, Pageable pageable) {
        throw new NotImplementedException("MakerFilamentService.listFilaments not yet implemented");
    }

    @Override
    public FilamentResponse createFilament(CreateFilamentRequest request) {
        throw new NotImplementedException("MakerFilamentService.createFilament not yet implemented");
    }

    @Override
    public FilamentResponse updateFilament(String filamentGuid, UpdateFilamentRequest request) {
        throw new NotImplementedException("MakerFilamentService.updateFilament not yet implemented");
    }

    @Override
    public FilamentResponse adjustStock(String filamentGuid, AdjustFilamentStockRequest request) {
        throw new NotImplementedException("MakerFilamentService.adjustStock not yet implemented");
    }

    @Override
    public void deleteFilament(String filamentGuid) {
        throw new NotImplementedException("MakerFilamentService.deleteFilament not yet implemented");
    }
}
