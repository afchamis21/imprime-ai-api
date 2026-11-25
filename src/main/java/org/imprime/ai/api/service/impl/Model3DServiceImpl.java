package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.model3d.Model3DCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.model3d.Model3DCatalogItemResponse;
import org.imprime.ai.api.http.response.model3d.Model3DDetailResponse;
import org.imprime.ai.api.http.response.model3d.ModelAuthorResponse;
import org.imprime.ai.api.http.response.model3d.ModelCustomizationResponse;
import org.imprime.ai.api.http.response.model3d.ScadParameterDefinition;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.ModelCustomization;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.ModelPricingFilter;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ModelCustomizationRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.Model3DService;
import org.imprime.ai.api.service.ObjectStorageService;
import org.imprime.ai.api.service.OrderManagementService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.imprime.ai.api.util.ScadParameterParser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Model3DServiceImpl implements Model3DService {
    private final Model3DRepository model3DRepository;
    private final ModelCustomizationRepository modelCustomizationRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ObjectStorageService objectStorageService;
    private final FavoriteServiceImpl favoriteService;
    private final JsonCodec jsonCodec;

    @Override
    public PageResponse<Model3DCatalogItemResponse> listModels(Model3DCatalogFilter filter, Pageable pageable) {
        List<Model3D> activeModels = model3DRepository.findAllByStatus(StatusCd.ACTIVE).stream()
                .filter(this::isPubliclyApproved)
                .toList();
        if (activeModels.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> authorIds = activeModels.stream()
                .map(Model3D::getAuthorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> authorsById = authorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Set<Long> companyIds = authorsById.values().stream()
                .map(User::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Company> companiesById = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        Set<Long> favoritedIds = favoriteService.findFavoritedTargetIds(FavoriteTargetType.MODEL_3D);

        List<Model3DCatalogItemResponse> items = activeModels.stream()
                .filter(model -> matchesFilter(model, authorsById.get(model.getAuthorUserId()), companiesById, filter))
                .sorted(buildComparator(pageable))
                .map(model -> toCatalogItem(model, authorsById.get(model.getAuthorUserId()), companiesById, favoritedIds))
                .toList();

        return PageUtils.paginate(items, pageable);
    }

    @Override
    @Transactional
    public Model3DDetailResponse getModelDetail(String slugOrGuid) {
        Model3D model = resolveActiveModel(slugOrGuid);
        long currentViews = model.getViewsCount() != null ? model.getViewsCount() : 0L;
        model.setViewsCount(currentViews + 1L);
        model3DRepository.save(model);

        ModelAuthorResponse author = resolveAuthor(model.getAuthorUserId());
        boolean favorited = favoriteService.isFavorited(FavoriteTargetType.MODEL_3D, model.getId());

        String modelFileUrl = model.getFileAssetId() != null
                ? fileAssetRepository.findById(model.getFileAssetId())
                .map(asset -> "/models/" + model.getGuid() + "/file")
                .orElse(null)
                : null;

        List<String> imageUrls = jsonCodec.readStringList(model.getImageUrlsJson());
        if (imageUrls.isEmpty() && model.getCoverImageUrl() != null && !model.getCoverImageUrl().isBlank()) {
            imageUrls = List.of(model.getCoverImageUrl());
        }

        return new Model3DDetailResponse(
                model.getGuid(),
                model.getSlug(),
                model.getName(),
                model.getPrice(),
                model.getDescription(),
                model.getCategory(),
                model.getRatingAvg() != null ? model.getRatingAvg() : BigDecimal.ZERO,
                model.getReviewsCount() != null ? model.getReviewsCount() : 0,
                model.getDownloadsCount() != null ? model.getDownloadsCount() : 0L,
                model.getViewsCount(),
                model.getEstimatedWeightGrams(),
                CatalogDtoMapper.toDimensionsDto(model.getWidthMm(), model.getDepthMm(), model.getHeightMm()),
                model.getRecommendedMaterial(),
                model.getFormat(),
                model.getLicense(),
                Boolean.TRUE.equals(model.getCustomizable()),
                modelFileUrl,
                jsonCodec.readStringList(model.getTagsJson()),
                favorited,
                author,
                imageUrls,
                model.getCreateDt()
        );
    }

    @Override
    public OrderManagementService.OrderFileContent downloadModelFile(String slugOrGuid) {
        Model3D model = resolveActiveModel(slugOrGuid);
        if (model.getFileAssetId() == null) {
            throw new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND);
        }

        FileAsset asset = fileAssetRepository.findById(model.getFileAssetId())
                .orElseThrow(() -> new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND));

        byte[] bytes = objectStorageService.downloadBytes(asset);
        String baseName = model.getSlug() != null && !model.getSlug().isBlank()
                ? model.getSlug()
                : model.getGuid();
        String fileName = asset.getName() != null && !asset.getName().isBlank()
                ? asset.getName()
                : baseName + "." + resolveExtension(model.getFormat());
        String contentType = resolveContentType(fileName, model.getFormat());

        return new OrderManagementService.OrderFileContent(fileName, contentType, bytes);
    }

    private String resolveExtension(FileFormat3D format) {
        if (format == null) {
            return "stl";
        }
        return switch (format) {
            case STL -> "stl";
            case THREE_MF -> "3mf";
            case OBJ -> "obj";
            case GLB -> "glb";
            case SCAD -> "scad";
            case GCODE -> "gcode";
        };
    }

    private String resolveContentType(String fileName, FileFormat3D format) {
        String lower = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".stl")) return "model/stl";
        if (lower.endsWith(".3mf")) return "model/3mf";
        if (lower.endsWith(".obj")) return "model/obj";
        if (lower.endsWith(".glb")) return "model/gltf-binary";
        if (lower.endsWith(".scad") || lower.endsWith(".gcode")) return "text/plain; charset=utf-8";

        if (format != null) {
            return switch (format) {
                case STL -> "model/stl";
                case THREE_MF -> "model/3mf";
                case OBJ -> "model/obj";
                case GLB -> "model/gltf-binary";
                case SCAD, GCODE -> "text/plain; charset=utf-8";
            };
        }
        return "application/octet-stream";
    }

    @Override
    public FavoriteStatusResponse addFavorite(String modelGuid) {
        Model3D model = model3DRepository.findByGuidAndStatus(modelGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));
        return favoriteService.upsertFavorite(FavoriteTargetType.MODEL_3D, model.getId(), model.getGuid());
    }

    @Override
    public void removeFavorite(String modelGuid) {
        Model3D model = model3DRepository.findByGuidAndStatus(modelGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));
        favoriteService.removeFavoriteByTarget(FavoriteTargetType.MODEL_3D, model.getId());
    }

    @Override
    public ModelCustomizationResponse getCustomization(String slugOrGuid) {
        Model3D model = resolveActiveModel(slugOrGuid);
        if (!Boolean.TRUE.equals(model.getCustomizable())) {
            throw new NotFoundException(MessageCd.MODEL_NOT_CUSTOMIZABLE);
        }

        Optional<ModelCustomization> customizationOpt = modelCustomizationRepository
                .findByModel3dIdAndStatus(model.getId(), StatusCd.ACTIVE);

        if (customizationOpt.isPresent()) {
            ModelCustomization customization = customizationOpt.get();
            List<ScadParameterDefinition> parameters = jsonCodec.readList(
                    customization.getParametersSchemaJson(),
                    ScadParameterDefinition.class
            );
            if (parameters.isEmpty() && customization.getScadCode() != null && !customization.getScadCode().isBlank()) {
                parameters = ScadParameterParser.parse(customization.getScadCode());
            }
            return new ModelCustomizationResponse(model.getGuid(), customization.getScadCode(), parameters);
        }

        if (model.getFormat() == FileFormat3D.SCAD && model.getFileAssetId() != null) {
            FileAsset asset = fileAssetRepository.findById(model.getFileAssetId())
                    .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_CUSTOMIZABLE));
            byte[] bytes = objectStorageService.downloadBytes(asset);
            String scadCode = new String(bytes, StandardCharsets.UTF_8);
            List<ScadParameterDefinition> parsedParams = ScadParameterParser.parse(scadCode);
            return new ModelCustomizationResponse(model.getGuid(), scadCode, parsedParams);
        }

        throw new NotFoundException(MessageCd.MODEL_NOT_CUSTOMIZABLE);
    }

    private Model3D resolveActiveModel(String slugOrGuid) {
        Model3D model = model3DRepository.findByGuidAndStatus(slugOrGuid, StatusCd.ACTIVE)
                .or(() -> model3DRepository.findBySlugAndStatus(slugOrGuid, StatusCd.ACTIVE))
                .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));
        if (!isPubliclyApproved(model) && !isAuthorCaller(model)) {
            throw new NotFoundException(MessageCd.MODEL_NOT_FOUND);
        }
        return model;
    }

    private boolean isAuthorCaller(Model3D model) {
        if (model.getAuthorUserId() == null) {
            return false;
        }
        return ServiceContext.getUser()
                .map(user -> Objects.equals(user.getId(), model.getAuthorUserId()))
                .orElse(false);
    }

    private boolean isPubliclyApproved(Model3D model) {
        return model.getModerationStatus() == null
                || ModelModerationStatus.APPROVED.equals(model.getModerationStatus());
    }

    private ModelAuthorResponse resolveAuthor(Long authorUserId) {
        if (authorUserId == null) {
            return CatalogDtoMapper.toModelAuthorResponse(null, null);
        }
        User author = userRepository.findById(authorUserId).orElse(null);
        Company company = (author != null && author.getCompanyId() != null)
                ? companyRepository.findById(author.getCompanyId()).orElse(null)
                : null;
        return CatalogDtoMapper.toModelAuthorResponse(author, company);
    }

    private Model3DCatalogItemResponse toCatalogItem(
            Model3D model,
            User author,
            Map<Long, Company> companiesById,
            Set<Long> favoritedIds) {
        Company authorCompany = (author != null && author.getCompanyId() != null)
                ? companiesById.get(author.getCompanyId())
                : null;
        List<String> tags = jsonCodec.readStringList(model.getTagsJson());
        List<String> images = jsonCodec.readStringList(model.getImageUrlsJson());
        String cover = CatalogDtoMapper.firstNonBlank(model.getCoverImageUrl(), images.isEmpty() ? null : images.getFirst());

        return new Model3DCatalogItemResponse(
                model.getGuid(),
                model.getSlug(),
                model.getName(),
                model.getCategory(),
                model.getPrice(),
                model.getRatingAvg() != null ? model.getRatingAvg() : BigDecimal.ZERO,
                model.getReviewsCount() != null ? model.getReviewsCount() : 0,
                model.getDownloadsCount() != null ? model.getDownloadsCount() : 0L,
                model.getViewsCount() != null ? model.getViewsCount() : 0L,
                model.getFormat(),
                model.getLicense(),
                Boolean.TRUE.equals(model.getCustomizable()),
                tags,
                cover,
                favoritedIds.contains(model.getId()),
                CatalogDtoMapper.toModelAuthorResponse(author, authorCompany),
                model.getCreateDt()
        );
    }

    private boolean matchesFilter(
            Model3D model,
            User author,
            Map<Long, Company> companiesById,
            Model3DCatalogFilter filter) {
        if (filter == null) {
            return true;
        }
        List<String> modelTags = jsonCodec.readStringList(model.getTagsJson());

        if (filter.query() != null && !filter.query().isBlank()) {
            String q = filter.query().trim().toLowerCase(Locale.ROOT);
            Company authorCompany = (author != null && author.getCompanyId() != null)
                    ? companiesById.get(author.getCompanyId())
                    : null;
            boolean textMatch = containsIgnoreCase(model.getName(), q)
                    || containsIgnoreCase(model.getDescription(), q)
                    || containsIgnoreCase(model.getCategory(), q)
                    || (author != null && containsIgnoreCase(author.getFullName(), q))
                    || (authorCompany != null && containsIgnoreCase(authorCompany.getName(), q))
                    || modelTags.stream().anyMatch(tag -> containsIgnoreCase(tag, q));
            if (!textMatch) {
                return false;
            }
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            if (model.getCategory() == null || !model.getCategory().equalsIgnoreCase(filter.category().trim())) {
                return false;
            }
        }
        if (filter.pricing() != null && filter.pricing() != ModelPricingFilter.ALL) {
            BigDecimal price = model.getPrice() != null ? model.getPrice() : BigDecimal.ZERO;
            if (filter.pricing() == ModelPricingFilter.FREE && price.compareTo(BigDecimal.ZERO) > 0) {
                return false;
            }
            if (filter.pricing() == ModelPricingFilter.PAID && price.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
        }
        if (filter.tags() != null && !filter.tags().isEmpty()) {
            Set<String> normalizedModelTags = modelTags.stream()
                    .map(t -> t.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            boolean anyTagMatch = filter.tags().stream()
                    .filter(Objects::nonNull)
                    .map(t -> t.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedModelTags::contains);
            if (!anyTagMatch) {
                return false;
            }
        }
        if (Boolean.TRUE.equals(filter.customizableOnly()) && !Boolean.TRUE.equals(model.getCustomizable())) {
            return false;
        }
        return true;
    }

    private Comparator<Model3D> buildComparator(Pageable pageable) {
        Comparator<Model3D> defaultComparator = Comparator
                .comparing((Model3D m) -> m.getDownloadsCount() != null ? m.getDownloadsCount() : 0L, Comparator.reverseOrder())
                .thenComparing(Model3D::getCreateDt, Comparator.nullsLast(Comparator.reverseOrder()));

        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultComparator;
        }

        Comparator<Model3D> combined = null;
        for (Sort.Order order : pageable.getSort()) {
            Comparator<Model3D> next = switch (order.getProperty()) {
                case "price" -> Comparator.comparing(Model3D::getPrice, Comparator.nullsLast(BigDecimal::compareTo));
                case "rating", "ratingAvg" -> Comparator.comparing(
                        Model3D::getRatingAvg, Comparator.nullsLast(BigDecimal::compareTo));
                case "downloadsCount" -> Comparator.comparingLong(m -> m.getDownloadsCount() != null ? m.getDownloadsCount() : 0L);
                case "viewsCount" -> Comparator.comparingLong(m -> m.getViewsCount() != null ? m.getViewsCount() : 0L);
                case "reviewsCount" -> Comparator.comparingInt(m -> m.getReviewsCount() != null ? m.getReviewsCount() : 0);
                case "createdAt", "createDt" -> Comparator.comparing(
                        Model3D::getCreateDt, Comparator.nullsLast(Comparator.naturalOrder()));
                case "name" -> Comparator.comparing(Model3D::getName, Comparator.nullsLast(String::compareToIgnoreCase));
                default -> null;
            };
            if (next != null) {
                if (order.isDescending()) {
                    next = next.reversed();
                }
                combined = combined == null ? next : combined.thenComparing(next);
            }
        }
        return combined != null ? combined : defaultComparator;
    }

    private boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }
}
