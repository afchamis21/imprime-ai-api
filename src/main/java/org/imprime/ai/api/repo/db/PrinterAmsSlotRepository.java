package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.PrinterAmsSlot;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PrinterAmsSlotRepository extends JpaRepository<PrinterAmsSlot, Long> {
    List<PrinterAmsSlot> findAllByAssetIdAndStatusOrderBySlotNumberAsc(Long assetId, StatusCd status);

    List<PrinterAmsSlot> findAllByAssetIdInAndStatusOrderBySlotNumberAsc(Collection<Long> assetIds, StatusCd status);
}
