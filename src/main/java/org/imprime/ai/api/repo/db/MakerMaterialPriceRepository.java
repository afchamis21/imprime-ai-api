package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.MakerMaterialPrice;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MakerMaterialPriceRepository extends JpaRepository<MakerMaterialPrice, Long> {

    List<MakerMaterialPrice> findAllByCompanyIdAndStatusOrderByMaterialAsc(Long companyId, StatusCd status);

    List<MakerMaterialPrice> findAllByCompanyIdInAndStatus(Collection<Long> companyIds, StatusCd status);

    Optional<MakerMaterialPrice> findByCompanyIdAndMaterialAndStatus(Long companyId, MaterialType material, StatusCd status);
}
