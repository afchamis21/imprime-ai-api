package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findAllByCompanyIdAndStatus(Long companyId, StatusCd status);

    List<Asset> findAllByCompanyIdInAndStatus(Collection<Long> companyIds, StatusCd status);

    List<Asset> findAllByCompanyIdAndPrinterStatusAndStatus(Long companyId, PrinterStatus printerStatus, StatusCd status);

    Optional<Asset> findByGuidAndStatus(String guid, StatusCd status);

    List<Asset> findAllByIdIn(Collection<Long> ids);

    long countByPrinterCatalogIdAndStatus(Long printerCatalogId, StatusCd status);

    @Query("""
            SELECT a.printerCatalogId, COUNT(a)
            FROM Asset a
            WHERE a.status = :status
              AND a.printerCatalogId IS NOT NULL
            GROUP BY a.printerCatalogId
            """)
    List<Object[]> countGroupedByPrinterCatalogId(@Param("status") StatusCd status);
}
