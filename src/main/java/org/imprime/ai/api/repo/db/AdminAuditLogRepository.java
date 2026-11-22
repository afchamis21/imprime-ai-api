package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.AdminAuditLog;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    Optional<AdminAuditLog> findByGuidAndStatus(String guid, StatusCd status);

    List<AdminAuditLog> findAllByStatusOrderByCreateDtDesc(StatusCd status);
}
