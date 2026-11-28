package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MakerPricingProfileRepository extends JpaRepository<MakerPricingProfile, Long> {

    Optional<MakerPricingProfile> findByCompanyIdAndStatus(Long companyId, StatusCd status);

    List<MakerPricingProfile> findAllByCompanyIdInAndStatus(Collection<Long> companyIds, StatusCd status);
}
