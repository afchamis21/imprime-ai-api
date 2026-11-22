package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.SaleFileResponse;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.springframework.data.domain.Pageable;

public interface MakerSaleFilesService {
    PageResponse<SaleFileResponse> listSaleFiles(String query, OrderStatusCd orderStatus, Pageable pageable);
}
