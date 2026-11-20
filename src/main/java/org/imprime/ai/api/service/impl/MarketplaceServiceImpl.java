package org.imprime.ai.api.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.marketplace.MarketplaceStatsResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.service.MarketplaceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketplaceServiceImpl implements MarketplaceService {
    private static final String CACHE_KEY = "marketplace-stats";

    private final CompanyRepository companyRepository;
    private final OrderStatusRepository orderStatusRepository;

    private final Cache<String, MarketplaceStatsResponse> statsCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1)
            .build();

    @Override
    public MarketplaceStatsResponse getStats() {
        return statsCache.get(CACHE_KEY, key -> computeStats());
    }

    private MarketplaceStatsResponse computeStats() {
        List<Company> activeMakers = companyRepository.findAllByStatus(StatusCd.ACTIVE);
        long totalMakers = activeMakers.size();

        BigDecimal ratingSum = BigDecimal.ZERO;
        int ratedCount = 0;
        Integer minLeadTime = null;
        Integer maxLeadTime = null;

        for (Company maker : activeMakers) {
            if (maker.getReviewsCount() != null && maker.getReviewsCount() > 0 && maker.getRatingAvg() != null) {
                ratingSum = ratingSum.add(maker.getRatingAvg());
                ratedCount++;
            }
            if (maker.getMinLeadTimeDays() != null && maker.getMinLeadTimeDays() > 0) {
                minLeadTime = minLeadTime == null
                        ? maker.getMinLeadTimeDays()
                        : Math.min(minLeadTime, maker.getMinLeadTimeDays());
            }
            if (maker.getMaxLeadTimeDays() != null && maker.getMaxLeadTimeDays() > 0) {
                maxLeadTime = maxLeadTime == null
                        ? maker.getMaxLeadTimeDays()
                        : Math.max(maxLeadTime, maker.getMaxLeadTimeDays());
            }
        }

        BigDecimal averageRating = ratedCount > 0
                ? ratingSum.divide(BigDecimal.valueOf(ratedCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        int effectiveMinDays = minLeadTime != null ? minLeadTime : 2;
        int effectiveMaxDays = maxLeadTime != null ? Math.max(effectiveMinDays, maxLeadTime) : Math.max(effectiveMinDays, 7);

        long totalCompletedOrders = orderStatusRepository.countOrdersWithLatestStatus(
                OrderStatusCd.COMPLETED.getCode(),
                StatusCd.ACTIVE.getCode()
        );

        return new MarketplaceStatsResponse(
                totalMakers,
                averageRating,
                effectiveMinDays,
                effectiveMaxDays,
                totalCompletedOrders
        );
    }
}
