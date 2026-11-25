package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FilamentRepository extends JpaRepository<Filament, Long> {
    List<Filament> findAllByCompanyIdAndStatus(Long companyId, StatusCd status);

    Optional<Filament> findByGuidAndStatus(String guid, StatusCd status);

    List<Filament> findAllByIdIn(Collection<Long> ids);

    long countByFilamentCatalogIdAndStatus(Long filamentCatalogId, StatusCd status);

    @Query("""
            SELECT f.filamentCatalogId, COUNT(f)
            FROM Filament f
            WHERE f.status = :status
              AND f.filamentCatalogId IS NOT NULL
            GROUP BY f.filamentCatalogId
            """)
    List<Object[]> countGroupedByFilamentCatalogId(@Param("status") StatusCd status);
}
