package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteModulesRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsBundleResponse;

public interface MakerQuoteSettingsService {
    MakerQuoteSettingsBundleResponse getSettings();

    MakerQuoteSettingsBundleResponse updateSettings(UpdateMakerQuoteModulesRequest request);
}
