package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.UserFavorite;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {
    Optional<UserFavorite> findByGuidAndStatus(String guid, StatusCd status);

    Optional<UserFavorite> findByUserIdAndTargetTypeAndTargetId(Long userId, FavoriteTargetType targetType, Long targetId);

    Page<UserFavorite> findAllByUserIdAndStatus(Long userId, StatusCd status, Pageable pageable);

    Page<UserFavorite> findAllByUserIdAndTargetTypeAndStatus(Long userId, FavoriteTargetType targetType, StatusCd status, Pageable pageable);

    List<UserFavorite> findAllByUserIdAndTargetTypeAndStatus(Long userId, FavoriteTargetType targetType, StatusCd status);
}
