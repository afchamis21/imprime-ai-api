package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySlug(String slug);

    Optional<Product> findByGuid(String guid);

    Optional<Product> findByGuidAndStatus(String guid, StatusCd status);

    Optional<Product> findBySlugAndStatus(String slug, StatusCd status);

    List<Product> findAllByStatus(StatusCd status);

    List<Product> findAllByOrderByCreateDtDesc();

    Page<Product> findAllByCompanyIdAndStatus(Long companyId, StatusCd status, Pageable pageable);

    List<Product> findAllByIdIn(Collection<Long> ids);
}
