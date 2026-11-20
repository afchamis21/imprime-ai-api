package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrintQueueItemRepository extends JpaRepository<PrintQueueItem, Long> {
    List<PrintQueueItem> findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(Long companyId, StatusCd status);

    List<PrintQueueItem> findAllByAssetIdAndJobStatusInAndStatus(
            Long assetId,
            Collection<PrintJobStatus> jobStatuses,
            StatusCd status
    );

    List<PrintQueueItem> findAllByAssetIdInAndJobStatusInAndStatus(
            Collection<Long> assetIds,
            Collection<PrintJobStatus> jobStatuses,
            StatusCd status
    );

    boolean existsByAssetIdAndJobStatusInAndStatus(
            Long assetId,
            Collection<PrintJobStatus> jobStatuses,
            StatusCd status
    );

    Optional<PrintQueueItem> findByGuidAndStatus(String guid, StatusCd status);

    List<PrintQueueItem> findAllByCompanyIdAndGuidInAndStatus(Long companyId, Collection<String> guids, StatusCd status);
}
