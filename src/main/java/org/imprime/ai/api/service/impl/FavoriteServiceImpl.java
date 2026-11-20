package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.user.UserFavoriteResponse;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.FavoriteService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    @Override
    public PageResponse<UserFavoriteResponse> listUserFavorites(FavoriteTargetType type, Pageable pageable) {
        throw new NotImplementedException("FavoriteService.listUserFavorites not yet implemented");
    }

    @Override
    public void removeFavorite(String favoriteGuid) {
        throw new NotImplementedException("FavoriteService.removeFavorite not yet implemented");
    }
}
