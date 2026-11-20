package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.MakerArchiveFile;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MakerArchiveFileRepository extends JpaRepository<MakerArchiveFile, Long> {
    List<MakerArchiveFile> findAllByCompanyIdAndStatus(Long companyId, StatusCd status);

    List<MakerArchiveFile> findAllByMakerProjectIdAndStatusOrderByCreateDtAsc(Long makerProjectId, StatusCd status);

    List<MakerArchiveFile> findAllByMakerProjectIdInAndStatus(Collection<Long> makerProjectIds, StatusCd status);

    Optional<MakerArchiveFile> findByGuidAndStatus(String guid, StatusCd status);

    List<MakerArchiveFile> findAllByIdIn(Collection<Long> ids);
}
