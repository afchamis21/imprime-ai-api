package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.MakerQuoteHistory;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MakerQuoteHistoryRepository extends JpaRepository<MakerQuoteHistory, Long> {
    List<MakerQuoteHistory> findAllByCompanyIdAndStatusOrderByCreateDtDesc(Long companyId, StatusCd status);

    Optional<MakerQuoteHistory> findByGuidAndStatus(String guid, StatusCd status);
}
