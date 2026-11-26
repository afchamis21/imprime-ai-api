package org.imprime.ai.api.repo.db;

import jakarta.persistence.LockModeType;
import org.imprime.ai.api.model.MakerProject;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MakerProjectRepository extends JpaRepository<MakerProject, Long> {
    List<MakerProject> findAllByCompanyIdAndStatus(Long companyId, StatusCd status);

    Optional<MakerProject> findByGuidAndStatus(String guid, StatusCd status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MakerProject> findForUpdateByGuidAndStatus(String guid, StatusCd status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MakerProject> findForUpdateByIdAndStatus(Long id, StatusCd status);

    List<MakerProject> findAllByIdIn(Collection<Long> ids);
}
