package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.user.UserFavoriteResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.UserFavorite;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserFavoriteRepository;
import org.imprime.ai.api.service.FavoriteService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageableSortMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {
    private static final Map<String, String> SORT_MAPPING = Map.of(
            "favoritedAt", "createDt",
            "createdAt", "createDt",
            "targetType", "targetType"
    );
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createDt");

    private final UserFavoriteRepository userFavoriteRepository;
    private final CompanyRepository companyRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final Model3DRepository model3DRepository;
    private final JsonCodec jsonCodec;

    @Transactional
    public FavoriteStatusResponse upsertFavorite(FavoriteTargetType targetType, Long targetId, String targetGuid) {
        User caller = ServiceContext.getUserOrThrow();
        UserFavorite favorite = userFavoriteRepository
                .findByUserIdAndTargetTypeAndTargetId(caller.getId(), targetType, targetId)
                .orElseGet(() -> {
                    UserFavorite created = new UserFavorite();
                    created.setUserId(caller.getId());
                    created.setTargetType(targetType);
                    created.setTargetId(targetId);
                    return created;
                });

        favorite.setStatus(StatusCd.ACTIVE);
        userFavoriteRepository.save(favorite);
        return new FavoriteStatusResponse(targetGuid, targetType, true);
    }

    @Transactional
    public void removeFavoriteByTarget(FavoriteTargetType targetType, Long targetId) {
        User caller = ServiceContext.getUserOrThrow();
        userFavoriteRepository
                .findByUserIdAndTargetTypeAndTargetId(caller.getId(), targetType, targetId)
                .ifPresent(existing -> {
                    if (StatusCd.ACTIVE.equals(existing.getStatus())) {
                        existing.setStatus(StatusCd.INACTIVE);
                        userFavoriteRepository.save(existing);
                    }
                });
    }

    public Set<Long> findFavoritedTargetIds(FavoriteTargetType targetType) {
        Optional<User> callerOpt = ServiceContext.getUser();
        if (callerOpt.isEmpty()) {
            return Set.of();
        }
        return userFavoriteRepository
                .findAllByUserIdAndTargetTypeAndStatus(callerOpt.get().getId(), targetType, StatusCd.ACTIVE)
                .stream()
                .map(UserFavorite::getTargetId)
                .collect(Collectors.toSet());
    }

    public boolean isFavorited(FavoriteTargetType targetType, Long targetId) {
        Optional<User> callerOpt = ServiceContext.getUser();
        if (callerOpt.isEmpty() || targetId == null) {
            return false;
        }
        return userFavoriteRepository
                .findByUserIdAndTargetTypeAndTargetId(callerOpt.get().getId(), targetType, targetId)
                .map(fav -> StatusCd.ACTIVE.equals(fav.getStatus()))
                .orElse(false);
    }

    @Override
    public PageResponse<UserFavoriteResponse> listUserFavorites(FavoriteTargetType type, Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        Pageable mappedPageable = PageableSortMapper.remap(pageable, SORT_MAPPING, DEFAULT_SORT);

        Page<UserFavorite> page = type == null
                ? userFavoriteRepository.findAllByUserIdAndStatus(caller.getId(), StatusCd.ACTIVE, mappedPageable)
                : userFavoriteRepository.findAllByUserIdAndTargetTypeAndStatus(caller.getId(), type, StatusCd.ACTIVE, mappedPageable);

        Map<FavoriteTargetType, Set<Long>> idsByType = new EnumMap<>(FavoriteTargetType.class);
        for (UserFavorite favorite : page.getContent()) {
            idsByType.computeIfAbsent(favorite.getTargetType(), ignored -> new HashSet<>()).add(favorite.getTargetId());
        }

        Set<Long> makerIds = idsByType.getOrDefault(FavoriteTargetType.MAKER, Set.of());
        Map<Long, Company> companiesById = makerIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(makerIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));
        Map<Long, Address> addressesByCompanyId = makerIds.isEmpty()
                ? Map.of()
                : CatalogDtoMapper.indexPrimaryAddressesByCompanyId(
                addressRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(EntityType.COMPANY, makerIds, StatusCd.ACTIVE)
        );

        Set<Long> productIds = idsByType.getOrDefault(FavoriteTargetType.PRODUCT, Set.of());
        Map<Long, Product> productsById = productIds.isEmpty()
                ? Map.of()
                : productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Set<Long> modelIds = idsByType.getOrDefault(FavoriteTargetType.MODEL_3D, Set.of());
        Map<Long, Model3D> modelsById = modelIds.isEmpty()
                ? Map.of()
                : model3DRepository.findAllByIdIn(modelIds).stream()
                .collect(Collectors.toMap(Model3D::getId, m -> m, (a, b) -> a));

        Page<UserFavoriteResponse> mapped = page.map(fav -> toFavoriteResponse(
                fav, companiesById, addressesByCompanyId, productsById, modelsById
        ));
        return PageResponse.from(mapped);
    }

    @Override
    @Transactional
    public void removeFavorite(String favoriteGuid) {
        User caller = ServiceContext.getUserOrThrow();
        UserFavorite favorite = userFavoriteRepository.findByGuidAndStatus(favoriteGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.FAVORITE_NOT_FOUND));

        if (!caller.getId().equals(favorite.getUserId())) {
            throw new ForbiddenException();
        }

        favorite.setStatus(StatusCd.INACTIVE);
        userFavoriteRepository.save(favorite);
    }

    private UserFavoriteResponse toFavoriteResponse(
            UserFavorite favorite,
            Map<Long, Company> companiesById,
            Map<Long, Address> addressesByCompanyId,
            Map<Long, Product> productsById,
            Map<Long, Model3D> modelsById) {
        return switch (favorite.getTargetType()) {
            case MAKER -> {
                Company company = companiesById.get(favorite.getTargetId());
                Address address = addressesByCompanyId.get(favorite.getTargetId());
                yield new UserFavoriteResponse(
                        favorite.getGuid(),
                        FavoriteTargetType.MAKER,
                        company != null ? company.getGuid() : null,
                        company != null ? company.getSlug() : null,
                        company != null ? company.getName() : null,
                        company != null ? CatalogDtoMapper.firstNonBlank(company.getTagline(), company.getSpecialty(), company.getBio()) : null,
                        address != null ? address.getCity() : null,
                        address != null ? address.getState() : null,
                        company != null && company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO,
                        company != null && company.getReviewsCount() != null ? company.getReviewsCount() : 0,
                        company != null ? CatalogDtoMapper.firstNonBlank(company.getAvatarUrl(), company.getCoverUrl()) : null,
                        favorite.getCreateDt()
                );
            }
            case PRODUCT -> {
                Product product = productsById.get(favorite.getTargetId());
                yield new UserFavoriteResponse(
                        favorite.getGuid(),
                        FavoriteTargetType.PRODUCT,
                        product != null ? product.getGuid() : null,
                        product != null ? product.getSlug() : null,
                        product != null ? product.getName() : null,
                        product != null ? product.getDescription() : null,
                        null,
                        null,
                        product != null && product.getRatingAvg() != null ? product.getRatingAvg() : BigDecimal.ZERO,
                        product != null && product.getReviewsCount() != null ? product.getReviewsCount() : 0,
                        product != null ? CatalogDtoMapper.resolveProductCoverUrl(product, jsonCodec) : null,
                        favorite.getCreateDt()
                );
            }
            case MODEL_3D -> {
                Model3D model = modelsById.get(favorite.getTargetId());
                yield new UserFavoriteResponse(
                        favorite.getGuid(),
                        FavoriteTargetType.MODEL_3D,
                        model != null ? model.getGuid() : null,
                        model != null ? model.getSlug() : null,
                        model != null ? model.getName() : null,
                        model != null ? model.getDescription() : null,
                        null,
                        null,
                        model != null && model.getRatingAvg() != null ? model.getRatingAvg() : BigDecimal.ZERO,
                        model != null && model.getReviewsCount() != null ? model.getReviewsCount() : 0,
                        model != null ? model.getCoverImageUrl() : null,
                        favorite.getCreateDt()
                );
            }
        };
    }
}
