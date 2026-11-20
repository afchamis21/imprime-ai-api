package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.maker.MakerStatsResponse;
import org.imprime.ai.api.model.enums.StatsRange;

import java.time.LocalDate;

public interface MakerAnalyticsService {
    MakerStatsResponse getStats(StatsRange range, LocalDate from, LocalDate to);
}
