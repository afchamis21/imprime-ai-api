package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.OrderStatusLkup;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusLkupRepository extends JpaRepository<OrderStatusLkup, OrderStatusCd> {
    List<OrderStatusLkup> findAllByStatus(StatusCd status);
}
