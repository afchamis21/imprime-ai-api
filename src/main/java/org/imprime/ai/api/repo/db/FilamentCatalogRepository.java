package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.FilamentCatalog;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FilamentCatalogRepository extends JpaRepository<FilamentCatalog, Long> {
    Optional<FilamentCatalog> findByGuidAndStatus(String guid, StatusCd status);

    Optional<FilamentCatalog> findByGuid(String guid);

    Optional<FilamentCatalog> findBySlugAndStatus(String slug, StatusCd status);

    List<FilamentCatalog> findAllByStatusOrderByBrandAscLineNameAscColorNameAsc(StatusCd status);

    List<FilamentCatalog> findAllByOrderByBrandAscLineNameAscColorNameAsc();

    List<FilamentCatalog> findAllByIdIn(Collection<Long> ids);

    boolean existsBySlug(String slug);

    boolean existsByBrandIgnoreCaseAndLineNameIgnoreCaseAndColorNameIgnoreCaseAndDiameterMmAndStatus(
            String brand,
            String lineName,
            String colorName,
            BigDecimal diameterMm,
            StatusCd status
    );

    boolean existsByBrandIgnoreCaseAndLineNameIgnoreCaseAndColorNameIgnoreCaseAndDiameterMmAndStatusAndIdNot(
            String brand,
            String lineName,
            String colorName,
            BigDecimal diameterMm,
            StatusCd status,
            Long id
    );
}
