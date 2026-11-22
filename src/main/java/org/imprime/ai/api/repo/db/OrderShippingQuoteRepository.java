package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.OrderShippingQuote;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderShippingQuoteRepository extends JpaRepository<OrderShippingQuote, Long> {
    List<OrderShippingQuote> findAllByGuidInAndBuyerIdAndStatusAndExpiresAtAfter(
            Collection<String> guids, Long buyerId, StatusCd status, Instant now);

    Optional<OrderShippingQuote> findByGuidAndBuyerIdAndVendorIdAndDeliveryAddressIdAndStatusAndExpiresAtAfter(
            String guid, Long buyerId, Long vendorId, Long deliveryAddressId, StatusCd status, Instant now);
}
