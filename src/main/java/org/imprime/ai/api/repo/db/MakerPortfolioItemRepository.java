package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.MakerPortfolioItem;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MakerPortfolioItemRepository extends JpaRepository<MakerPortfolioItem, Long> {
    Page<MakerPortfolioItem> findAllByCompanyIdAndStatus(Long companyId, StatusCd status, Pageable pageable);

    List<MakerPortfolioItem> findAllByCompanyIdInAndStatusOrderByCreateDtDesc(Collection<Long> companyIds, StatusCd status);

    Optional<MakerPortfolioItem> findByGuidAndStatus(String guid, StatusCd status);
}
