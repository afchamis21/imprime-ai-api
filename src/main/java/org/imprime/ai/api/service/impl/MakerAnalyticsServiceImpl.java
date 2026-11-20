package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.maker.MakerStatsResponse;
import org.imprime.ai.api.model.enums.StatsRange;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerAnalyticsService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MakerAnalyticsServiceImpl implements MakerAnalyticsService {

    @Override
    public MakerStatsResponse getStats(StatsRange range, LocalDate from, LocalDate to) {
        throw new NotImplementedException("MakerAnalyticsService.getStats not yet implemented");
    }
}
