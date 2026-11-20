package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.ModelCustomization;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelCustomizationRepository extends JpaRepository<ModelCustomization, Long> {
    Optional<ModelCustomization> findByModel3dIdAndStatus(Long model3dId, StatusCd status);
}
