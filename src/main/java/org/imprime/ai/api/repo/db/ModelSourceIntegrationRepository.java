package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.ModelSourceIntegration;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelSourceIntegrationRepository extends JpaRepository<ModelSourceIntegration, Long> {
    Page<ModelSourceIntegration> findAllByCompanyIdAndStatus(Long companyId, StatusCd status, Pageable pageable);

    Optional<ModelSourceIntegration> findByGuidAndStatus(String guid, StatusCd status);
}
