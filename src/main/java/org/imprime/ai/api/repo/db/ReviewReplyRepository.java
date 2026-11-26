package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.ReviewReply;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {
    List<ReviewReply> findAllByReviewIdInAndStatusOrderByCreateDtAscIdAsc(Collection<Long> reviewIds, StatusCd status);
}
