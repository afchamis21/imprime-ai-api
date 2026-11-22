package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface Model3DRepository extends JpaRepository<Model3D, Long> {
    boolean existsBySlug(String slug);

    Optional<Model3D> findByGuid(String guid);

    Optional<Model3D> findByGuidAndStatus(String guid, StatusCd status);

    Optional<Model3D> findBySlugAndStatus(String slug, StatusCd status);

    List<Model3D> findAllByStatus(StatusCd status);

    List<Model3D> findAllByOrderByCreateDtDesc();

    long countByStatus(StatusCd status);

    List<Model3D> findAllByIdIn(Collection<Long> ids);

    List<Model3D> findAllByAuthorUserIdInAndStatusOrderByCreateDtDesc(Collection<Long> authorUserIds, StatusCd status);
}
