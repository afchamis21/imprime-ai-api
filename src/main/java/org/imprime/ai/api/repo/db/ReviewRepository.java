package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Review;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType targetType, Long targetId, StatusCd status, Pageable pageable);

    List<Review> findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType targetType, Long targetId, StatusCd status);

    Page<Review> findAllByAuthorUserIdAndStatus(Long authorUserId, StatusCd status, Pageable pageable);

    Page<Review> findAllByAuthorUserIdAndTargetTypeAndStatus(Long authorUserId, ReviewTargetType targetType, StatusCd status, Pageable pageable);

    boolean existsByAuthorUserIdAndTargetTypeAndTargetIdAndStatus(Long authorUserId, ReviewTargetType targetType, Long targetId, StatusCd status);

    boolean existsByAuthorUserIdAndTargetTypeAndOrderIdAndStatus(Long authorUserId, ReviewTargetType targetType, Long orderId, StatusCd status);

    List<Review> findAllByAuthorUserIdAndTargetTypeAndTargetIdAndStatus(Long authorUserId, ReviewTargetType targetType, Long targetId, StatusCd status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Review> findOneByGuidAndStatus(String guid, StatusCd status);

    Optional<Review> findByGuidAndStatus(String guid, StatusCd status);
}
