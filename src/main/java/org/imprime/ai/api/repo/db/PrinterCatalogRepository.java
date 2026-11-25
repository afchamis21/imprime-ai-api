package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrinterCatalogRepository extends JpaRepository<PrinterCatalog, Long> {
    Optional<PrinterCatalog> findByGuidAndStatus(String guid, StatusCd status);

    Optional<PrinterCatalog> findByGuid(String guid);

    Optional<PrinterCatalog> findBySlugAndStatus(String slug, StatusCd status);

    List<PrinterCatalog> findAllByStatusOrderByBrandAscModelNameAsc(StatusCd status);

    List<PrinterCatalog> findAllByOrderByBrandAscModelNameAsc();

    List<PrinterCatalog> findAllByIdIn(Collection<Long> ids);

    boolean existsBySlug(String slug);

    boolean existsByBrandIgnoreCaseAndModelNameIgnoreCaseAndStatus(String brand, String modelName, StatusCd status);

    boolean existsByBrandIgnoreCaseAndModelNameIgnoreCaseAndStatusAndIdNot(String brand, String modelName, StatusCd status, Long id);
}
