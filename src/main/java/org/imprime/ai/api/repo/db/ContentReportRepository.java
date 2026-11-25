package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.ContentReport;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {
    Optional<ContentReport> findByGuidAndStatus(String guid, StatusCd status);

    boolean existsByProtocol(String protocol);

    List<ContentReport> findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd status);

    Page<ContentReport> findAllByReporterUserIdAndStatusOrderByCreateDtDesc(
            Long reporterUserId,
            StatusCd status,
            Pageable pageable
    );

    boolean existsByReporterUserIdAndTargetTypeAndTargetIdAndReportStatusInAndStatus(
            Long reporterUserId,
            ReportTargetType targetType,
            Long targetId,
            Collection<ReportStatus> reportStatuses,
            StatusCd status
    );

    long countByReportStatusInAndStatus(Collection<ReportStatus> reportStatuses, StatusCd status);

    List<ContentReport> findAllByTargetTypeAndTargetIdAndStatusOrderByCreateDtDesc(
            ReportTargetType targetType,
            Long targetId,
            StatusCd status
    );
}
