package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.user.UserFavoriteResponse;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.springframework.data.domain.Pageable;

public interface FavoriteService {
    PageResponse<UserFavoriteResponse> listUserFavorites(FavoriteTargetType type, Pageable pageable);

    void removeFavorite(String favoriteGuid);
}
