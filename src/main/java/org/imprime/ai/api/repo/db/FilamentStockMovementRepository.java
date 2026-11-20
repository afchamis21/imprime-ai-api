package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.FilamentStockMovement;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FilamentStockMovementRepository extends JpaRepository<FilamentStockMovement, Long> {
    List<FilamentStockMovement> findAllByFilamentIdAndStatusOrderByCreateDtDesc(Long filamentId, StatusCd status);
}
