package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MakerQuoteSettingsRepository extends JpaRepository<MakerQuoteSettings, Long> {
    List<MakerQuoteSettings> findByCompanyIdAndStatusOrderByExecutionOrderAscIdAsc(Long companyId, StatusCd status);

    Optional<MakerQuoteSettings> findByCompanyIdAndQuoteSettingsIdAndStatus(
            Long companyId,
            Long quoteSettingsId,
            StatusCd status
    );
}
