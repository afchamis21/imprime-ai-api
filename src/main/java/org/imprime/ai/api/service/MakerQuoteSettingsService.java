package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteSettingsRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsResponse;

public interface MakerQuoteSettingsService {
    MakerQuoteSettingsResponse getSettings();

    MakerQuoteSettingsResponse updateSettings(UpdateMakerQuoteSettingsRequest request);
}
